import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
import torchvision.transforms as transforms
import os
import numpy as np
import cv2
import torch.nn.functional as F
from PIL import Image
class Vimeo90KDataset(Dataset):
    def __init__(self, data_root, list_file, is_training=True):
        self.data_root = data_root
        self.is_training = is_training
        
        with open(list_file, 'r') as f:
            self.sequences = [line.strip() for line in f]

        self.transform = transforms.Compose([
            transforms.ToTensor(),  # Converts to [0, 1] range
        ])
        
    def __len__(self):
        return len(self.sequences)
    
    def __getitem__(self, idx):
        sequence_path = os.path.join(self.data_root, 'sequence', self.sequences[idx])
        
        frames = []
        for i in range(1, 8):  
            frame_path = os.path.join(sequence_path, f'im{i}.png')
            frame = Image.open(frame_path).convert('RGB')
            frame = self.transform(frame)
            frames.append(frame)
        
        frames = torch.stack(frames)  
        
        y_frames = []
        for i in range(7):
            rgb_frame = frames[i].permute(1, 2, 0).numpy()  # here it is: [H, W, 3]
            yuv_frame = self.rgb_to_yuv(rgb_frame)
            y_channel = yuv_frame[:, :, 0]  # to extract Y channel only
            y_frames.append(torch.tensor(y_channel, dtype=torch.float32))
        
        y_frames = torch.stack(y_frames)  # here it is: [7, H, W]
        
        # For training, using the middle frame as target and processing it (makes more sense)
        if self.is_training:
            # Use middle frame (4th frame) as the main frame
            target_frame = y_frames[3]  
            
            # Random crop for training (optional)
            # if self.is_training:
            #     h, w = target_frame.shape
            #     crop_size = 128  # Smaller patches for training
            #     if h > crop_size and w > crop_size:
            #         top = np.random.randint(0, h - crop_size)
            #         left = np.random.randint(0, w - crop_size)
            #         target_frame = target_frame[top:top+crop_size, left:left+crop_size]
            
            return {
                'frames': target_frame.unsqueeze(0),  # [1, H, W]
                'original_rgb': frames[3],  # Keep RGB for perceptual loss
                'sequence_name': self.sequences[idx]
            }
        else:
            # For testing, return all frames
            return {
                'frames': y_frames,
                'original_rgb': frames,
                'sequence_name': self.sequences[idx]
            }
    
    @staticmethod
    def rgb_to_yuv(rgb_frame):
        """Convert RGB to YUV using BT.601"""
        conversion_matrix = np.array([
            [0.299, 0.587, 0.114],
            [-0.14713, -0.28886, 0.436],
            [0.615, -0.51499, -0.10001]
        ])
        
        yuv = np.dot(rgb_frame.reshape(-1, 3), conversion_matrix.T)
        yuv = yuv.reshape(rgb_frame.shape)
        
        # Y channel is in range [0, 1], UV channels are in range [-0.5, 0.5]
        # Adjust UV channels to [0, 1] range
        yuv[:, :, 1:] = yuv[:, :, 1:] + 0.5
        
        return yuv

class DilatedConvBlock(nn.Module):
  def __init__(self, in_channels, out_channels, dilation_rate, kernel_size=3):
      super(DilatedConvBlock, self).__init__()
      padding = (kernel_size - 1) // 2 * dilation_rate
      self.conv = nn.Conv2d(in_channels, out_channels, kernel_size, 
                            padding=padding, dilation=dilation_rate)
      self.prelu = nn.PReLU()
      
  def forward(self, x):
      return self.prelu(self.conv(x))



class DeepPerceptualPreprocessor(nn.Module):
    def __init__(self, in_channels=1, output_activation='sigmoid'):  
        super(DeepPerceptualPreprocessor, self).__init__()
        
        self.layers = nn.ModuleList()
        self.layers.append(DilatedConvBlock(in_channels, 64, dilation_rate=1))
        dilation_rates = [2, 4, 8, 16, 8, 4, 2, 1]
        for d_rate in dilation_rates:
            self.layers.append(DilatedConvBlock(64, 64, dilation_rate=d_rate))
        
        self.final_conv = nn.Conv2d(64, in_channels, kernel_size=3, padding=1)
        self.output_activation = output_activation
        self.alpha = nn.Parameter(torch.ones(1) * 0.1)

        if output_activation == 'sigmoid':
            self.output_act = nn.Sigmoid()  # [0, 1]
        elif output_activation == 'tanh':
            self.output_act = nn.Tanh()      # [-1, 1]
        

        
    def forward(self, x):
        identity = x
        out = x
        for layer in self.layers:
            out = layer(out)

        out = self.final_conv(out)
        out = self.alpha * out + identity
        
        if self.output_activation == None:
            return out
        elif self.output_activation == 'clamp':
            return torch.clamp(out, 0, 1)
        else:
            return self.output_act(out)


class VirtualCodec:
    
    @staticmethod
    def rgb_to_yuv(rgb_frame):
        conversion_matrix = np.array([
            [0.299, 0.587, 0.114],
            [-0.14713, -0.28886, 0.436],
            [0.615, -0.51499, -0.10001]
        ])
        
        yuv = np.dot(rgb_frame.reshape(-1, 3), conversion_matrix.T)
        return yuv.reshape(rgb_frame.shape)
    
    @staticmethod
    def yuv_to_rgb(yuv_frame):
        conversion_matrix = np.array([
            [1.0, 0.0, 1.13983],
            [1.0, -0.39465, -0.58060],
            [1.0, 2.03211, 0.0]
        ])
        
        rgb = np.dot(yuv_frame.reshape(-1, 3), conversion_matrix.T)
        return np.clip(rgb.reshape(yuv_frame.shape), 0, 255)
    
    # The forward transform is typically a two-dimensional discrete transform (DCT)
    @staticmethod
    def block_dct_4x4(block):
        # Simplified integer DCT transform matrix
        transform_matrix = np.array([
            [1, 1, 1, 1],
            [2, 1, -1, -2],
            [1, -1, -1, 1],
            [1, -2, 2, -1]
        ])
        
        # Forward transform
        temp = np.dot(transform_matrix, block)
        result = np.dot(temp, transform_matrix.T)
        
        # Scale factors (simplified)
        scale = np.array([[0.25, 0.158, 0.25, 0.158],
                         [0.158, 0.1, 0.158, 0.1],
                         [0.25, 0.158, 0.25, 0.158],
                         [0.158, 0.1, 0.158, 0.1]])
        
        return result * scale
    
    @staticmethod
    def block_idct_4x4(block):
        transform_matrix = np.array([
            [1, 1, 1, 1],
            [2, 1, -1, -2],
            [1, -1, -1, 1],
            [1, -2, 2, -1]
        ])
        
        scale = np.array([[0.25, 0.158, 0.25, 0.158],
                        [0.158, 0.1, 0.158, 0.1],
                        [0.25, 0.158, 0.25, 0.158],
                        [0.158, 0.1, 0.158, 0.1]])
        
        temp = np.dot(transform_matrix.T, block)
        result = np.dot(temp, transform_matrix)
        
        return result * scale

    # The transformed and scaled yt is then quantized by divided by a quantization vallue  Qstep
    @staticmethod
    def quantize(dct_coeffs, qp):
        qstep = 0.625 * (2 ** (qp / 6))
        
        # The rounding operation in quantization is non-differentiable, so it is approximated with additive uniform noise
        if torch.is_tensor(dct_coeffs):
            noise = torch.rand_like(dct_coeffs) - 0.5
            quantized = dct_coeffs / qstep + noise
        else:
            quantized = np.round(dct_coeffs / qstep)
        
        return quantized, qstep
    
    @staticmethod
    def divisive_normalization(coeffs, epsilon=1e-10):
        kernel_size = 3
        if torch.is_tensor(coeffs):
            kernel = torch.ones(1, 1, kernel_size, kernel_size) / (kernel_size ** 2)
            kernel = kernel.to(coeffs.device)
            
            local_mean = F.conv2d(coeffs.unsqueeze(0).unsqueeze(0), kernel, padding=1)
            local_var = F.conv2d(coeffs.unsqueeze(0).unsqueeze(0) ** 2, kernel, padding=1) - local_mean ** 2
            local_std = torch.sqrt(torch.clamp(local_var, min=epsilon))
            
            normalized = coeffs / (local_std.squeeze() + epsilon)
        else:
            from scipy.ndimage import uniform_filter
            local_mean = uniform_filter(coeffs, size=kernel_size, mode='constant')
            local_var = uniform_filter(coeffs ** 2, size=kernel_size, mode='constant') - local_mean ** 2
            local_std = np.sqrt(np.maximum(local_var, epsilon))
            normalized = coeffs / (local_std + epsilon)
        
        return normalized

class DPPTrainer:
    
    def __init__(self, data_root, device='cuda'):
        self.device = device
        self.data_root = data_root
        
        # Initialize networks
        self.preprocessor = DeepPerceptualPreprocessor().to(device)
        # self.perceptual_model = PerceptualModel().to(device)
        
        # Freezeing perceptual model which is pre-trained now
        # for param in self.perceptual_model.parameters():
        #     param.requires_grad = False
        
        self.codec = VirtualCodec()
        self.optimizer = torch.optim.Adam(self.preprocessor.parameters(), lr=1e-4)
        self.lambda_rate = 0.01
        self.gamma_perceptual = 0.01
        self.alpha_l1 = 0.2
        self.beta_ssim = 0.8
        
        self.train_dataset = Vimeo90KDataset(
            data_root=data_root,
            list_file=os.path.join(data_root, 'sep_trainlist.txt'),
            is_training=True
        )
        
        self.test_dataset = Vimeo90KDataset(
            data_root=data_root,
            list_file=os.path.join(data_root, 'sep_testlist.txt'),
            is_training=False
        )
        
        self.train_loader = DataLoader(
            self.train_dataset,
            batch_size=8,
            shuffle=True,
            num_workers=0,
            pin_memory=True
        )
        
        self.test_loader = DataLoader(
            self.test_dataset,
            batch_size=1,
            shuffle=False,
            num_workers=0,
            pin_memory=True
        )
        
    def compute_rate_loss(self, dct_coeffs):
        """
        Compute rate loss for DCT coefficients
        dct_coeffs: [batch_size, channels, height, width]
        """
        batch_size, channels, height, width = dct_coeffs.shape
        total_rate = 0.0
        
        for b in range(batch_size):
            for c in range(channels):
                # Process each channel of each image separately
                coeffs = dct_coeffs[b, c]  # [height, width]
                normalized = self.codec.divisive_normalization(coeffs)
                
                scale = torch.std(normalized)
                if scale > 0:
                    log_likelihood = -torch.abs(normalized) / scale - torch.log(2 * scale)
                else:
                    # Handle case where std is 0
                    log_likelihood = torch.zeros_like(normalized)
                
                rate = -torch.sum(log_likelihood) / np.log(2)  # Convert to bits
                total_rate += rate
        
        return total_rate / (batch_size * channels)  # Average rate

    
    # def compute_perceptual_loss(self, reconstructed_rgb):
    #     """Compute perceptual loss using frozen perceptual model"""
    #     # Get MOS distribution
    #     mos_dist = self.perceptual_model(reconstructed_rgb)
        
    #     # Expected MOS score
    #     scores = torch.arange(1, 6, dtype=torch.float32).to(self.device)
    #     expected_mos = torch.sum(mos_dist * scores, dim=1)
        
    #     # Loss is negative MOS (we want to maximize MOS)
    #     return -torch.mean(expected_mos)
    
    def compute_fidelity_loss(self, original, reconstructed):
        l1_loss = F.l1_loss(reconstructed, original)
        
        ssim_loss = 1 - self.ssim(original, reconstructed)
        
        return self.alpha_l1 * l1_loss + self.beta_ssim * ssim_loss
    

    def ssim(self, x, y):
        C1 = 0.01 ** 2
        C2 = 0.03 ** 2
        
        mu_x = F.avg_pool2d(x, 3, 1, padding=1)
        mu_y = F.avg_pool2d(y, 3, 1, padding=1)
        
        sigma_x = F.avg_pool2d(x ** 2, 3, 1, padding=1) - mu_x ** 2
        sigma_y = F.avg_pool2d(y ** 2, 3, 1, padding=1) - mu_y ** 2
        sigma_xy = F.avg_pool2d(x * y, 3, 1, padding=1) - mu_x * mu_y
        
        ssim = ((2 * mu_x * mu_y + C1) * (2 * sigma_xy + C2)) / \
               ((mu_x ** 2 + mu_y ** 2 + C1) * (sigma_x + sigma_y + C2))
        
        return torch.mean(ssim)
    
    def process_frame_virtual_codec(self, frame, qp):
        batch_size, _, h, w = frame.shape
        
        dct_coeffs = torch.zeros(batch_size, 1, h, w, device=self.device, dtype=frame.dtype)
        
        for b in range(batch_size):
            for i in range(0, h - 3, 4):
                for j in range(0, w - 3, 4):
                    block = frame[b, 0, i:i+4, j:j+4]
                    dct_block = self.codec.block_dct_4x4(block.detach().cpu().numpy())
                    dct_coeffs[b, 0, i:i+4, j:j+4] = torch.tensor(
                        dct_block,
                        device=self.device,
                        dtype=frame.dtype
                    )
        
        quantized, qstep = self.codec.quantize(dct_coeffs, qp)
        
        if self.preprocessor.training:
            noise = torch.rand_like(quantized) - 0.5
            dequantized = (quantized + noise) * qstep
        else:
            dequantized = torch.round(quantized) * qstep
        
        reconstructed = torch.zeros(batch_size, 1, h, w, device=self.device, dtype=frame.dtype)
        
        for b in range(batch_size):
            for i in range(0, h - 3, 4):
                for j in range(0, w - 3, 4):
                    block = dequantized[b, 0, i:i+4, j:j+4]
                    idct_block = self.codec.block_idct_4x4(block.detach().cpu().numpy())
                    reconstructed[b, 0, i:i+4, j:j+4] = torch.tensor(
                        idct_block,
                        device=self.device,
                        dtype=frame.dtype
                    )
        
        if h % 4 != 0:
            reconstructed[:, :, -(h % 4):, :] = frame[:, :, -(h % 4):, :]
        if w % 4 != 0:
            reconstructed[:, :, :, -(w % 4):] = frame[:, :, :, -(w % 4):]
        
        return reconstructed, dct_coeffs

    
    def train_step(self, batch):
        self.preprocessor.train()
        
        frames = batch['frames'].to(self.device)
        original_rgb = batch['original_rgb'].to(self.device)
        qp = np.random.randint(0, 6)
        preprocessed = self.preprocessor(frames)
        reconstructed, dct_coeffs = self.process_frame_virtual_codec(preprocessed, qp)
        rate_loss = self.compute_rate_loss(dct_coeffs)

        # For perceptual loss, we need to reconstruct RGB
        # Here we use the original RGB and replace Y channel
        reconstructed_rgb = original_rgb.clone()
        # perceptual_loss = self.compute_perceptual_loss(reconstructed_rgb)
        
        fidelity_loss = self.compute_fidelity_loss(frames, reconstructed)
        
        # Total loss
        # total_loss = (self.gamma_perceptual * perceptual_loss + 
        #              self.lambda_rate * rate_loss + 
        #              fidelity_loss)
        
        total_loss = (self.lambda_rate*rate_loss + fidelity_loss)

        self.optimizer.zero_grad()
        total_loss.backward()
        self.optimizer.step()
        
        return {
            'total_loss': total_loss.item(),
            # 'perceptual_loss': perceptual_loss.item(),
            'rate_loss': rate_loss.item(),
            'fidelity_loss': fidelity_loss.item()
        }
    
    def validate(self):
        self.preprocessor.eval()
        
        total_losses = {
            'total_loss': 0,
            # 'perceptual_loss': 0,
            'rate_loss': 0,
            'fidelity_loss': 0
        }
        
        with torch.no_grad():
            for batch_idx, batch in enumerate(self.test_loader):
                if batch_idx > 50: 
                    break
                
                frames = batch['frames'][:, 3:4, :, :].to(self.device)  
                original_rgb = batch['original_rgb'][:, 3, :, :, :].to(self.device)
                
                qp = 3
                
                preprocessed = self.preprocessor(frames.unsqueeze(1))
                
                reconstructed, dct_coeffs = self.process_frame_virtual_codec(preprocessed, qp)
                
                rate_loss = self.compute_rate_loss(dct_coeffs)
                fidelity_loss = self.compute_fidelity_loss(frames.unsqueeze(1), reconstructed)
                
                total_loss = (self.lambda_rate * rate_loss + 
                             fidelity_loss)
                
                total_losses['total_loss'] += total_loss.item()
                total_losses['rate_loss'] += rate_loss.item()
                total_losses['fidelity_loss'] += fidelity_loss.item()
        
        num_batches = min(len(self.test_loader), 50)
        for key in total_losses:
            total_losses[key] /= num_batches
        
        return total_losses
    
    def train(self, num_epochs=10):
        best_val_loss = float('inf')
        
        for epoch in range(num_epochs):
            print(f"\nEpoch {epoch + 1}/{num_epochs}")
            
            train_losses = {
                'total_loss': 0,
                # 'perceptual_loss': 0,
                'rate_loss': 0,
                'fidelity_loss': 0
            }
            
            for batch_idx, batch in enumerate(self.train_loader):
                if batch_idx % 100 == 0:
                    print(f"Training batch {batch_idx}/{len(self.train_loader)}")
                
                losses = self.train_step(batch)
                
                for key in losses:
                    train_losses[key] += losses[key]
                
                if batch_idx % 100 == 0:
                    print(f"  Total Loss: {losses['total_loss']:.4f}, "
                        #   f"Perceptual: {losses['perceptual_loss']:.4f}, "
                          f"Rate: {losses['rate_loss']:.4f}, "
                          f"Fidelity: {losses['fidelity_loss']:.4f}")
            
            for key in train_losses:
                train_losses[key] /= len(self.train_loader)
            
            print(f"\nTraining Average - Total: {train_losses['total_loss']:.4f}, "
                #   f"Perceptual: {train_losses['perceptual_loss']:.4f}, "
                  f"Rate: {train_losses['rate_loss']:.4f}, "
                  f"Fidelity: {train_losses['fidelity_loss']:.4f}")
            
            print("\nValidating...")
            val_losses = self.validate()
            
            print(f"Validation - Total: {val_losses['total_loss']:.4f}, "
                #   f"Perceptual: {val_losses['perceptual_loss']:.4f}, "
                  f"Rate: {val_losses['rate_loss']:.4f}, "
                  f"Fidelity: {val_losses['fidelity_loss']:.4f}")
            
            if val_losses['total_loss'] < best_val_loss:
                best_val_loss = val_losses['total_loss']
                torch.save({
                    'epoch': epoch,
                    'preprocessor_state_dict': self.preprocessor.state_dict(),
                    'optimizer_state_dict': self.optimizer.state_dict(),
                    'val_loss': best_val_loss,
                }, f'best_dpp_model_vimeo90k.pth')
                print(f"Saved best model with validation loss: {best_val_loss:.4f}")
    
    # def inference(self, frame):
    #     self.preprocessor.eval()
        
    #     with torch.no_grad():
    #         if isinstance(frame, np.ndarray):
    #             frame_tensor = torch.tensor(frame).float()
    #             if frame_tensor.max() > 1:
    #                 frame_tensor = frame_tensor / 255.0
    #             frame_tensor = frame_tensor.to(self.device)
    #         else:
    #             frame_tensor = frame
            
    #         if frame_tensor.dim() == 2:
    #             frame_tensor = frame_tensor.unsqueeze(0).unsqueeze(0)
    #         elif frame_tensor.dim() == 3:
    #             frame_tensor = frame_tensor.unsqueeze(0)
            
    #         # Preprocess
    #         preprocessed = self.preprocessor(frame_tensor)
            
    #         # Convert back to numpy
    #         output = preprocessed.squeeze().cpu().numpy()
    #         output = np.clip(output * 255, 0, 255).astype(np.uint8)
            
    #     return output
    
   
    


if __name__ == "__main__":

    data_root = "Vimeo-fortest"
    trainer = DPPTrainer(
        data_root=data_root,
        device='cuda' if torch.cuda.is_available() else 'cpu'
    )
    
    print(f"Device: {trainer.device}")
    print(f"Training samples: {len(trainer.train_dataset)}")
    print(f"Test samples: {len(trainer.test_dataset)}")
    
    trainer.train(num_epochs=20)
    
    # # Example inference on a single image
    # test_image_path = os.path.join(data_root, 'sequences', '00001/0001/im4.png')
    # if os.path.exists(test_image_path):
    #     # Load image and convert to Y channel
    #     img = cv2.imread(test_image_path)
    #     yuv = cv2.cvtColor(img, cv2.COLOR_BGR2YUV)
    #     y_channel = yuv[:, :, 0]
        
    #     # Preprocess
    #     preprocessed_y = trainer.inference(y_channel)
        
    #     # Save comparison
    #     cv2.imwrite('original_y.png', y_channel)
    #     cv2.imwrite('preprocessed_y.png', preprocessed_y)
    #     print("Saved comparison images")


