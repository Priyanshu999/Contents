import torch
import torch.nn as nn
import torch.nn.functional as F
import numpy as np
from torchvision import models, transforms
from torch.utils.data import Dataset, DataLoader
import cv2
import os
from PIL import Image
from scipy.fftpack import dct, idct
import glob


class Vimeo90kDataset(Dataset):
    """Dataset loader for Vimeo-90k triplet dataset"""
    def __init__(self, root_dir, transform=None, mode='train'):
        self.root_dir = root_dir
        self.transform = transform
        self.mode = mode
        
        # Read sequence list
        list_file = os.path.join(root_dir, f'tri_{mode}list.txt')
        with open(list_file, 'r') as f:
            self.sequences = [line.strip() for line in f.readlines()]
        
        # Default transform if none provided
        if self.transform is None:
            self.transform = transforms.Compose([
                transforms.ToTensor(),
                transforms.Normalize(mean=[0.485, 0.456, 0.406], 
                                   std=[0.229, 0.224, 0.225])
            ])
    
    def __len__(self):
        return len(self.sequences)
    
    def __getitem__(self, idx):
        seq_path = self.sequences[idx]
        seq_dir = os.path.join(self.root_dir, 'sequences', seq_path)
        
        # Load 3 frames
        frames = []
        for i in range(1, 4):  # frames: im1.png, im2.png, im3.png
            frame_path = os.path.join(seq_dir, f'im{i}.png')
            frame = Image.open(frame_path).convert('RGB')
            frames.append(frame)
        
        # Apply transforms
        if self.transform:
            frames = [self.transform(frame) for frame in frames]
        
        # Stack frames: shape (3, 3, 256, 448) -> (frames, channels, height, width)
        frames = torch.stack(frames, dim=0)
        
        return {
            'frames': frames,
            'sequence_name': seq_path
        }


class DilatedConvBlock(nn.Module):
    """Dilated convolution block for larger receptive fields"""
    def __init__(self, in_channels, out_channels, dilation_rate, kernel_size=3):
        super(DilatedConvBlock, self).__init__()
        padding = (kernel_size - 1) // 2 * dilation_rate
        self.conv = nn.Conv2d(in_channels, out_channels, kernel_size, 
                              padding=padding, dilation=dilation_rate)
        self.batch_norm = nn.BatchNorm2d(out_channels)
        self.prelu = nn.PReLU()
        
    def forward(self, x):
        return self.prelu(self.batch_norm(self.conv(x)))


class DeepPerceptualPreprocessor(nn.Module):
    """Main DPP network for preprocessing frames - updated for RGB input"""
    def __init__(self, in_channels=3):  # RGB input
        super(DeepPerceptualPreprocessor, self).__init__()
        
        # Build network with dilated convolutions
        self.layers = nn.ModuleList()
        
        # First layer - convert from RGB to feature space
        self.layers.append(DilatedConvBlock(in_channels, 64, dilation_rate=1))
        
        # Middle layers with increasing dilation rates
        dilation_rates = [2, 4, 8, 16, 8, 4, 2, 1]
        for d_rate in dilation_rates:
            self.layers.append(DilatedConvBlock(64, 64, dilation_rate=d_rate))
        
        # Final layer - convert back to RGB
        self.final_conv = nn.Conv2d(64, in_channels, kernel_size=3, padding=1)
        
    def forward(self, x):
        # Input shape: (batch, 3, 3, 256, 448) or (batch, 3, 256, 448)
        batch_size = x.size(0)
        
        # Handle temporal dimension if present
        if x.dim() == 5:  # (batch, frames, channels, height, width)
            frames, channels, height, width = x.size(1), x.size(2), x.size(3), x.size(4)
            x = x.view(batch_size * frames, channels, height, width)
            process_temporal = True
        else:
            process_temporal = False
        
        # Residual connection
        identity = x
        
        # Pass through dilated conv blocks
        out = x
        for layer in self.layers:
            out = layer(out)
        
        # Final convolution
        out = self.final_conv(out)
        
        # Add residual connection
        out = out + identity
        
        # Ensure output is in valid range
        out = torch.tanh(out)  # Output in [-1, 1] range for normalized input
        
        # Reshape back to temporal format if needed
        if process_temporal:
            out = out.view(batch_size, frames, channels, height, width)
        
        return out


class PerceptualModel(nn.Module):
    """No-reference perceptual quality assessment model"""
    def __init__(self):
        super(PerceptualModel, self).__init__()
        
        # Use pre-trained VGG16 as backbone
        vgg16 = models.vgg16(pretrained=True)
        
        # Extract features from multiple layers
        self.features = nn.ModuleDict({
            'conv1_2': nn.Sequential(*list(vgg16.features[:4])),
            'conv2_2': nn.Sequential(*list(vgg16.features[4:9])),
            'conv3_3': nn.Sequential(*list(vgg16.features[9:16])),
            'conv4_3': nn.Sequential(*list(vgg16.features[16:23])),
            'conv5_3': nn.Sequential(*list(vgg16.features[23:30]))
        })
        
        # Global average pooling for each feature map
        self.gap = nn.AdaptiveAvgPool2d(1)
        
        # Calculate total feature dimensions
        # VGG16 feature dimensions: 64, 128, 256, 512, 512
        total_features = 64 + 128 + 256 + 512 + 512
        
        # Fully connected layers to predict MOS distribution
        self.fc = nn.Sequential(
            nn.Linear(total_features, 512),
            nn.ReLU(),
            nn.Dropout(0.5),
            nn.Linear(512, 256),
            nn.ReLU(),
            nn.Dropout(0.5),
            nn.Linear(256, 5)  # 5 scores (1-5 MOS)
        )
        
    def forward(self, x):
        # Handle batch of frames
        if x.dim() == 5:  # (batch, frames, channels, height, width)
            batch_size, frames = x.size(0), x.size(1)
            x = x.view(batch_size * frames, *x.shape[2:])
        else:
            batch_size, frames = x.size(0), 1
        
        # Extract multi-scale features
        features = []
        out = x
        
        for name, layer in self.features.items():
            out = layer(out)
            pooled = self.gap(out)
            features.append(pooled.view(pooled.size(0), -1))
        
        # Concatenate all features
        combined = torch.cat(features, dim=1)
        
        # Predict MOS distribution
        scores = self.fc(combined)
        distribution = F.softmax(scores, dim=1)
        
        # Reshape back if we had multiple frames
        if frames > 1:
            distribution = distribution.view(batch_size, frames, -1)
        
        return distribution


class VirtualCodec:
    """Virtual codec components for rate estimation"""
    
    @staticmethod
    def rgb_to_yuv(rgb_frame):
        """Convert RGB to YUV using BT.709 for HD content"""
        # BT.709 conversion matrix (more appropriate for HD video)
        conversion_matrix = np.array([
            [0.2126, 0.7152, 0.0722],
            [-0.1146, -0.3854, 0.5000],
            [0.5000, -0.4542, -0.0458]
        ])
        
        # Normalize input if needed
        if rgb_frame.max() > 1.0:
            rgb_frame = rgb_frame / 255.0
        
        yuv = np.dot(rgb_frame.reshape(-1, 3), conversion_matrix.T)
        yuv = yuv.reshape(rgb_frame.shape)
        
        # Scale UV components
        yuv[:, :, 1:] = yuv[:, :, 1:] + 0.5
        
        return yuv
    
    @staticmethod
    def yuv_to_rgb(yuv_frame):
        """Convert YUV back to RGB"""
        # Inverse BT.709 conversion matrix
        conversion_matrix = np.array([
            [1.0, 0.0, 1.5748],
            [1.0, -0.1873, -0.4681],
            [1.0, 1.8556, 0.0]
        ])
        
        # Adjust UV components
        yuv_adjusted = yuv_frame.copy()
        yuv_adjusted[:, :, 1:] = yuv_adjusted[:, :, 1:] - 0.5
        
        rgb = np.dot(yuv_adjusted.reshape(-1, 3), conversion_matrix.T)
        rgb = np.clip(rgb.reshape(yuv_frame.shape), 0, 1)
        
        return rgb
    
    @staticmethod
    def block_dct_8x8(block):
        """8x8 DCT transform (more standard for video coding)"""
        return dct(dct(block.T, norm='ortho').T, norm='ortho')
    
    @staticmethod
    def block_idct_8x8(block):
        """8x8 Inverse DCT transform"""
        return idct(idct(block.T, norm='ortho').T, norm='ortho')
    
    @staticmethod
    def quantize(dct_coeffs, qp, is_training=True):
        """Quantization with QP parameter"""
        # Standard quantization matrix (JPEG-like)
        quant_matrix = np.array([
            [16, 11, 10, 16, 24, 40, 51, 61],
            [12, 12, 14, 19, 26, 58, 60, 55],
            [14, 13, 16, 24, 40, 57, 69, 56],
            [14, 17, 22, 29, 51, 87, 80, 62],
            [18, 22, 37, 56, 68, 109, 103, 77],
            [24, 35, 55, 64, 81, 104, 113, 92],
            [49, 64, 78, 87, 103, 121, 120, 101],
            [72, 92, 95, 98, 112, 100, 103, 99]
        ])
        
        # Scale quantization matrix with QP
        qstep = quant_matrix * (qp + 1)
        
        if torch.is_tensor(dct_coeffs):
            qstep = torch.tensor(qstep, dtype=dct_coeffs.dtype, device=dct_coeffs.device)
            
            if is_training:
                # Add uniform noise during training (straight-through estimator)
                noise = torch.rand_like(dct_coeffs) - 0.5
                quantized = dct_coeffs / qstep + noise
            else:
                quantized = torch.round(dct_coeffs / qstep)
        else:
            quantized = np.round(dct_coeffs / qstep)
        
        return quantized, qstep


class DPPTrainer:
    """Training framework for Deep Perceptual Preprocessing - updated for Vimeo-90k"""
    
    def __init__(self, device='cuda'):
        self.device = device
        
        # Initialize networks
        self.preprocessor = DeepPerceptualPreprocessor().to(device)
        self.perceptual_model = PerceptualModel().to(device)
        
        # Freeze perceptual model (pre-trained)
        for param in self.perceptual_model.parameters():
            param.requires_grad = False
        
        # Virtual codec
        self.codec = VirtualCodec()
        
        # Optimizer with learning rate scheduling
        self.optimizer = torch.optim.Adam(
            self.preprocessor.parameters(), 
            lr=1e-4, 
            weight_decay=1e-5
        )
        self.scheduler = torch.optim.lr_scheduler.StepLR(
            self.optimizer, 
            step_size=10, 
            gamma=0.5
        )
        
        # Loss weights
        self.lambda_rate = 0.01
        self.gamma_perceptual = 0.1
        self.alpha_l1 = 0.2
        self.beta_ssim = 0.8
        
    def compute_rate_loss(self, frames):
        """Compute rate loss using DCT coefficient entropy"""
        batch_size, channels, height, width = frames.shape
        total_rate = 0
        
        # Process each frame
        for b in range(batch_size):
            frame = frames[b]
            # Convert to YUV and process Y channel
            frame_np = frame.permute(1, 2, 0).cpu().numpy()
            yuv_frame = self.codec.rgb_to_yuv(frame_np)
            y_channel = yuv_frame[:, :, 0]
            
            # Process 8x8 blocks
            h_blocks, w_blocks = height // 8, width // 8
            dct_coeffs = []
            
            for i in range(h_blocks):
                for j in range(w_blocks):
                    block = y_channel[i*8:(i+1)*8, j*8:(j+1)*8]
                    dct_block = self.codec.block_dct_8x8(block)
                    dct_coeffs.append(dct_block.flatten())
            
            # Concatenate all coefficients
            all_coeffs = np.concatenate(dct_coeffs)
            
            # Estimate entropy (simplified)
            # Use histogram to estimate probability distribution
            hist, _ = np.histogram(all_coeffs, bins=256, density=True)
            # Avoid log(0)
            hist = hist[hist > 0]
            entropy = -np.sum(hist * np.log2(hist + 1e-10))
            
            total_rate += entropy
        
        return torch.tensor(total_rate / batch_size, device=self.device)
    
    def compute_perceptual_loss(self, frames):
        """Compute perceptual loss using frozen perceptual model"""
        # Denormalize frames for perceptual model
        mean = torch.tensor([0.485, 0.456, 0.406]).to(self.device)
        std = torch.tensor([0.229, 0.224, 0.225]).to(self.device)
        
        # Handle temporal dimension
        if frames.dim() == 5:
            batch_size, num_frames = frames.size(0), frames.size(1)
            frames_flat = frames.view(batch_size * num_frames, *frames.shape[2:])
        else:
            frames_flat = frames
        
        # Denormalize
        denorm_frames = frames_flat * std.view(1, 3, 1, 1) + mean.view(1, 3, 1, 1)
        denorm_frames = torch.clamp(denorm_frames, 0, 1)
        
        # Get MOS distribution
        mos_dist = self.perceptual_model(denorm_frames)
        
        # Expected MOS score
        scores = torch.arange(1, 6, dtype=torch.float32).to(self.device)
        if mos_dist.dim() == 3:  # Multiple frames
            expected_mos = torch.sum(mos_dist * scores.view(1, 1, -1), dim=2)
            expected_mos = torch.mean(expected_mos, dim=1)  # Average over frames
        else:
            expected_mos = torch.sum(mos_dist * scores, dim=1)
        
        # Loss is negative MOS (we want to maximize MOS)
        return -torch.mean(expected_mos)
    
    def compute_fidelity_loss(self, original, reconstructed):
        """Compute fidelity loss (L1 + MS-SSIM)"""
        # Handle temporal dimension
        if original.dim() == 5:
            batch_size, num_frames = original.size(0), original.size(1)
            original = original.view(batch_size * num_frames, *original.shape[2:])
            reconstructed = reconstructed.view(batch_size * num_frames, *reconstructed.shape[2:])
        
        # L1 loss
        l1_loss = F.l1_loss(reconstructed, original)
        
        # SSIM loss
        ssim_loss = 1 - self.ssim(original, reconstructed)
        
        return self.alpha_l1 * l1_loss + self.beta_ssim * ssim_loss
    
    def ssim(self, x, y, window_size=11):
        """SSIM computation"""
        C1 = 0.01 ** 2
        C2 = 0.03 ** 2
        
        mu_x = F.avg_pool2d(x, window_size, 1, padding=window_size//2)
        mu_y = F.avg_pool2d(y, window_size, 1, padding=window_size//2)
        
        sigma_x = F.avg_pool2d(x ** 2, window_size, 1, padding=window_size//2) - mu_x ** 2
        sigma_y = F.avg_pool2d(y ** 2, window_size, 1, padding=window_size//2) - mu_y ** 2
        sigma_xy = F.avg_pool2d(x * y, window_size, 1, padding=window_size//2) - mu_x * mu_y
        
        ssim = ((2 * mu_x * mu_y + C1) * (2 * sigma_xy + C2)) / \
               ((mu_x ** 2 + mu_y ** 2 + C1) * (sigma_x + sigma_y + C2))
        
        return torch.mean(ssim)
    
    def train_step(self, batch):
        """Single training step"""
        self.preprocessor.train()
        
        # Get input frames: (batch, 3, 3, 256, 448)
        frames = batch['frames'].to(self.device)
        batch_size, num_frames, channels, height, width = frames.shape
        
        # Process center frame (frame 2) for preprocessing
        center_frame = frames[:, 1]  # Shape: (batch, 3, 256, 448)
        
        # Preprocess center frame
        preprocessed = self.preprocessor(center_frame)
        
        # Compute losses
        rate_loss = self.compute_rate_loss(preprocessed)
        perceptual_loss = self.compute_perceptual_loss(preprocessed)
        fidelity_loss = self.compute_fidelity_loss(center_frame, preprocessed)
        
        # Total loss
        total_loss = (self.gamma_perceptual * perceptual_loss + 
                     self.lambda_rate * rate_loss + 
                     fidelity_loss)
        
        # Backpropagation
        self.optimizer.zero_grad()
        total_loss.backward()
        
        # Gradient clipping
        torch.nn.utils.clip_grad_norm_(self.preprocessor.parameters(), max_norm=1.0)
        
        self.optimizer.step()
        
        return {
            'total_loss': total_loss.item(),
            'perceptual_loss': perceptual_loss.item(),
            'rate_loss': rate_loss.item(),
            'fidelity_loss': fidelity_loss.item()
        }
    
    def validate(self, val_loader):
        """Validation step"""
        self.preprocessor.eval()
        
        total_losses = {'total': 0, 'perceptual': 0, 'rate': 0, 'fidelity': 0}
        num_batches = 0
        
        with torch.no_grad():
            for batch in val_loader:
                frames = batch['frames'].to(self.device)
                center_frame = frames[:, 1]
                
                preprocessed = self.preprocessor(center_frame)
                
                rate_loss = self.compute_rate_loss(preprocessed)
                perceptual_loss = self.compute_perceptual_loss(preprocessed)
                fidelity_loss = self.compute_fidelity_loss(center_frame, preprocessed)
                
                total_loss = (self.gamma_perceptual * perceptual_loss + 
                             self.lambda_rate * rate_loss + 
                             fidelity_loss)
                
                total_losses['total'] += total_loss.item()
                total_losses['perceptual'] += perceptual_loss.item()
                total_losses['rate'] += rate_loss.item()
                total_losses['fidelity'] += fidelity_loss.item()
                num_batches += 1
        
        # Average losses
        for key in total_losses:
            total_losses[key] /= num_batches
        
        return total_losses
    
    def inference(self, frame):
        """Inference mode - preprocess frame for actual codec"""
        self.preprocessor.eval()
        
        with torch.no_grad():
            # Convert frame to tensor if needed
            if isinstance(frame, np.ndarray):
                if frame.dtype == np.uint8:
                    frame = frame.astype(np.float32) / 255.0
                
                # Convert to tensor and normalize
                frame_tensor = torch.tensor(frame).permute(2, 0, 1).float()
                transform = transforms.Normalize(mean=[0.485, 0.456, 0.406], 
                                               std=[0.229, 0.224, 0.225])
                frame_tensor = transform(frame_tensor)
                frame_tensor = frame_tensor.unsqueeze(0).to(self.device)
            else:
                frame_tensor = frame
            
            # Preprocess
            preprocessed = self.preprocessor(frame_tensor)
            
            # Denormalize
            mean = torch.tensor([0.485, 0.456, 0.406]).to(self.device)
            std = torch.tensor([0.229, 0.224, 0.225]).to(self.device)
            
            denorm = preprocessed * std.view(1, 3, 1, 1) + mean.view(1, 3, 1, 1)
            denorm = torch.clamp(denorm, 0, 1)
            
            # Convert back to numpy
            output = denorm.squeeze().permute(1, 2, 0).cpu().numpy()
            output = (output * 255).astype(np.uint8)
            
        return output


def create_data_loaders(root_dir, batch_size=8, num_workers=4):
    """Create data loaders for training and validation"""
    
    # Data transforms
    train_transform = transforms.Compose([
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], 
                           std=[0.229, 0.224, 0.225])
    ])
    
    val_transform = transforms.Compose([
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], 
                           std=[0.229, 0.224, 0.225])
    ])
    
    # Create datasets
    train_dataset = Vimeo90kDataset(root_dir, transform=train_transform, mode='train')
    val_dataset = Vimeo90kDataset(root_dir, transform=val_transform, mode='test')
    
    # Create data loaders
    train_loader = DataLoader(
        train_dataset, 
        batch_size=batch_size, 
        shuffle=True, 
        num_workers=num_workers,
        pin_memory=True
    )
    
    val_loader = DataLoader(
        val_dataset, 
        batch_size=batch_size, 
        shuffle=False, 
        num_workers=num_workers,
        pin_memory=True
    )
    
    return train_loader, val_loader


def train_model(root_dir, num_epochs=50, batch_size=8, save_path='dpp_model.pth'):
    """Complete training function using Vimeo-90k dataset"""
    
    # Set device
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    print(f"Using device: {device}")
    
    # Initialize trainer
    trainer = DPPTrainer(device=device)
    
    # Create data loaders
    print("Loading Vimeo-90k dataset...")
    train_loader, val_loader = create_data_loaders(root_dir, batch_size=batch_size)
    
    print(f"Training samples: {len(train_loader.dataset)}")
    print(f"Validation samples: {len(val_loader.dataset)}")
    
    # Training loop
    best_val_loss = float('inf')
    
    for epoch in range(num_epochs):
        print(f"\nEpoch {epoch + 1}/{num_epochs}")
        print("-" * 50)
        
        # Training phase
        trainer.preprocessor.train()
        train_losses = {'total': 0, 'perceptual': 0, 'rate': 0, 'fidelity': 0}
        num_batches = 0
        
        for batch_idx, batch in enumerate(train_loader):
            losses = trainer.train_step(batch)
            
            for key in train_losses:
                train_losses[key] += losses[key + '_loss']
            num_batches += 1
            
            # Print progress every 100 batches
            if (batch_idx + 1) % 100 == 0:
                print(f"  Batch {batch_idx + 1}/{len(train_loader)}: "
                      f"Loss = {losses['total_loss']:.4f}")
        
        # Average training losses
        for key in train_losses:
            train_losses[key] /= num_batches
        
        # Validation phase
        print("  Validating...")
        val_losses = trainer.validate(val_loader)
        
        # Print epoch results
        print(f"  Train Loss: {train_losses['total']:.4f} "
              f"(P: {train_losses['perceptual']:.4f}, "
              f"R: {train_losses['rate']:.4f}, "
              f"F: {train_losses['fidelity']:.4f})")
        
        print(f"  Val Loss: {val_losses['total']:.4f} "
              f"(P: {val_losses['perceptual']:.4f}, "
              f"R: {val_losses['rate']:.4f}, "
              f"F: {val_losses['fidelity']:.4f})")
        
        # Save best model
        if val_losses['total'] < best_val_loss:
            best_val_loss = val_losses['total']
            torch.save({
                'epoch': epoch,
                'model_state_dict': trainer.preprocessor.state_dict(),
                'optimizer_state_dict': trainer.optimizer.state_dict(),
                'scheduler_state_dict': trainer.scheduler.state_dict(),
                'val_loss': best_val_loss,
                'train_loss': train_losses['total']
            }, save_path)
            print(f"  New best model saved! Val Loss: {best_val_loss:.4f}")
        
        # Step scheduler
        trainer.scheduler.step()
        
        # Print current learning rate
        current_lr = trainer.scheduler.get_last_lr()[0]
        print(f"  Learning Rate: {current_lr:.6f}")
    
    print(f"\nTraining completed! Best validation loss: {best_val_loss:.4f}")
    return trainer


def test_inference(model_path, root_dir, num_samples=5):
    """Test inference on sample frames"""
    
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    
    # Load trained model
    trainer = DPPTrainer(device=device)
    checkpoint = torch.load(model_path, map_location=device)
    trainer.preprocessor.load_state_dict(checkpoint['model_state_dict'])
    trainer.preprocessor.eval()
    
    print(f"Loaded model from epoch {checkpoint['epoch']}")
    
    # Create test dataset
    test_dataset = Vimeo90kDataset(root_dir, mode='test')
    
    # Test on random samples
    import random
    indices = random.sample(range(len(test_dataset)), num_samples)
    
    print(f"\nTesting inference on {num_samples} samples...")
    
    for i, idx in enumerate(indices):
        sample = test_dataset[idx]
        frames = sample['frames']
        seq_name = sample['sequence_name']
        
        # Get center frame
        center_frame = frames[1]  # Shape: (3, 256, 448)
        
        # Convert to numpy for inference
        # Denormalize first
        mean = torch.tensor([0.485, 0.456, 0.406])
        std = torch.tensor([0.229, 0.224, 0.225])
        denorm_frame = center_frame * std.view(3, 1, 1) + mean.view(3, 1, 1)
        denorm_frame = torch.clamp(denorm_frame, 0, 1)
        
        # Convert to numpy (H, W, C)
        input_frame = denorm_frame.permute(1, 2, 0).numpy()
        input_frame = (input_frame * 255).astype(np.uint8)
        
        # Preprocess
        preprocessed_frame = trainer.inference(input_frame)
        
        print(f"  Sample {i+1} ({seq_name}):")
        print(f"    Input shape: {input_frame.shape}")
        print(f"    Output shape: {preprocessed_frame.shape}")
        print(f"    Input range: [{input_frame.min()}, {input_frame.max()}]")
        print(f"    Output range: [{preprocessed_frame.min()}, {preprocessed_frame.max()}]")
        
        # Optional: Save sample frames for visual inspection
        save_dir = "inference_samples"
        if not os.path.exists(save_dir):
            os.makedirs(save_dir)
        
        cv2.imwrite(f"{save_dir}/sample_{i+1}_input.png", 
                   cv2.cvtColor(input_frame, cv2.COLOR_RGB2BGR))
        cv2.imwrite(f"{save_dir}/sample_{i+1}_output.png", 
                   cv2.cvtColor(preprocessed_frame, cv2.COLOR_RGB2BGR))
    
    print(f"Sample frames saved in '{save_dir}' directory")


# Example usage
if __name__ == "__main__":
    # Set the path to your Vimeo-90k dataset
    # Download from: http://toflow.csail.mit.edu/
    # Expected structure:
    # vimeo_triplet/
    # ├── sequences/
    # │   ├── 00001/
    # │   │   ├── 0001/
    # │   │   │   ├── im1.png
    # │   │   │   ├── im2.png
    # │   │   │   └── im3.png
    # │   │   └── ...
    # │   └── ...
    # ├── tri_trainlist.txt
    # └── tri_testlist.txt
    
    root_dir = '/path/to/vimeo_triplet'  # Update this path!
    
    # Check if dataset exists
    if not os.path.exists(root_dir):
        print(f"Dataset not found at {root_dir}")
        print("Please download Vimeo-90k dataset and update the path.")
        print("Dataset URL: http://toflow.csail.mit.edu/")
        exit(1)
    
    # Check required files
    required_files = ['tri_trainlist.txt', 'tri_testlist.txt', 'sequences']
    missing_files = [f for f in required_files if not os.path.exists(os.path.join(root_dir, f))]
    
    if missing_files:
        print(f"Missing required files/directories: {missing_files}")
        print("Please ensure you have the complete Vimeo-90k triplet dataset.")
        exit(1)
    
    print("Dataset validation passed!")
    
    # Train the model
    print("Starting training on Vimeo-90k dataset...")
    trained_model = train_model(
        root_dir=root_dir,
        num_epochs=50,
        batch_size=8,  # Adjust based on your GPU memory
        save_path='dpp_vimeo90k_model.pth'
    )
    
    # Test inference
    print("\nTesting inference...")
    test_inference('dpp_vimeo90k_model.pth', root_dir, num_samples=5)
