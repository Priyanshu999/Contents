Thanks for the clarification. I’ll find 5–8 high-quality, recent (2020–2025) papers that focus on perceptual preprocessing for video or image coding, with an emphasis on HVS modeling, perceptual quality metrics, deep learning approaches, and adaptive preprocessing. I’ll then incorporate these papers along with the ones you’ve already provided into a structured 25–30 slide research survey presentation.

I’ll let you know as soon as the content is ready.


Slide 1: **Background & Motivation**

* Ultra-high-definition video streaming is pervasive (e.g. >80% of Internet traffic is video). Bandwidth constraints make better compression essential.
* Video codecs must trade off bitrate vs perceptual quality (e.g. texture/detail preservation).
* Traditional codecs (H.264/AVC, H.265/HEVC, H.266/VVC, AV1) rely on hand-engineered modules. Deep‐learning codecs can improve RD but often need complex new encoder/decoders.
* **Perceptual preprocessing** applies filters or DNNs to frames before standard encoding, boosting perceptual quality/efficiency without changing the decoder.

Slide 2: **Key Challenges in Perceptual Preprocessing**

* **Complex HVS**: The human visual system (HVS) has varying sensitivity to spatial frequencies, luminance, contrast, and motion. Preprocessors must respect these perceptual factors.
* **Metric Gap**: Simple metrics (PSNR/MSE) correlate poorly with perception. Even advanced full-reference metrics (SSIM, VMAF) have blind spots (e.g. color, HDR, temporal artifacts).
* **Implementation Constraints**: Preprocessor must be *codec-agnostic* (no bitstream changes) and computationally efficient (real-time or near-real-time for live video).
* **Content Diversity**: Scenes vary (animation vs natural vs noisy). A fixed filter can over-smooth edges or under-filter noise in some content. Adaptivity to content characteristics is needed.
* **Subjective Quality**: Ultimately, perceptual gains must be confirmed via human studies; objective gains in BD-rate or SSIM do not always match viewer preference.

Slide 3: **Human Visual System (HVS) Fundamentals**

* **Foveation**: Human acuity is highest at fixation and falls off in periphery → opportunities for foveated coding or pre-filtering less important regions.
* **Contrast Sensitivity**: HVS is less sensitive to high-frequency detail and noise (the contrast sensitivity function drops at high spatial frequencies).
* **Masking Effects**: Distortions are less visible in bright areas or regions of complex texture (luminance and texture masking).
* **Temporal Sensitivity**: Fast motion or flicker can mask artifacts (temporal contrast sensitivity); stable objects need higher fidelity.
* **Just-Noticeable-Difference (JND)**: Models compute visibility thresholds at each pixel; distortions below the JND are imperceptible. Preprocessors often use JND to limit filtering.

Slide 4: **Perceptual Quality Metrics**

* **PSNR/MSE**: Simple pixel-wise fidelity; fast but poorly correlated with human judgment.
* **SSIM / MS-SSIM**: Full-reference metrics comparing luminance, contrast, and structure in local windows.  MS-SSIM adds multi-scale analysis for better perceptual accuracy.
* **VMAF**: A 0–100 full-reference metric by Netflix (fusion of VIF, detail loss, temporal features via SVM) shown to predict subjective video quality. Widely adopted for codec evaluation.
* **Other Metrics**: VIF, DLM (detail loss), LPIPS (deep-learning image metric) capture various perceptual aspects. No-reference metrics (NIQE, BRISQUE, NR-VMAF) allow quality estimation without ground truth – useful for monitoring.

Slide 5: **Deep Learning Approaches & Perceptual Losses**

* **Perceptual Losses**: Use pretrained networks (e.g. VGG) to measure feature-space distance; encourages outputs that “look” similar even if pixel-different.
* **Adversarial Losses**: GAN discriminators push outputs toward the natural image manifold (used in generative compression schemes to improve textures).
* **Composite Training**: State-of-the-art preprocessors combine multiple losses. For example, Chadha *et al.* train their DNN with L1+SSIM fidelity loss plus a learned no-reference IQA loss and a motion-based bitrate loss.
* **Proxy Codecs**: Because standard codecs are non-differentiable, some works train with a differentiable proxy network that simulates compression (e.g. Lu *et al.* introduce a proxy for BPG codec during training).
* **Neural Codec Integration**: Some methods map pixel frames into a feature space (e.g. FVC, DFVC models) so that neural encoders can compress more efficiently. Preprocessing here often aims to remove “noise” in feature channels.

Slide 6: **Adaptive Perceptual Filters (Vidal *et al.*, 2021)**

* Introduce **BilAWA** and **TBil**: two adaptive pre-filters merging bilateral and Adaptive Weighting Average (AWA) methods.
* Filtering strength is controlled by a JND model: imperceptible details (fine noise/textures) are smoothed while preserving visible edges.
* Goal: maximize encoder efficiency by removing only “visually irrelevant” information.
* **Results**: When used before MPEG-4/AVC, BilAWA yields an average 19.3% bitrate saving (up to 28.7%) at *constant perceived quality*. Similar gains seen for HEVC.
* **Strengths/Limits**: Easy to integrate (any encoder), real-time implementation. However, purely spatial filtering can oversmooth if JND is misestimated; does not explicitly exploit temporal redundancy.

Slide 7: **Deep Image Pre-Editing Network (Talebi *et al.*, 2021)**

* **Pre-Editing Network (PEN)** for JPEG: a cascade of (1) smoothing CNN and (2) patch-based Spatial Transformer (STN).
* **Process**: The network *edits* the image (smooths + small geometric warps), then feeds it to a standard JPEG encoder. The network is trained end-to-end.
* **Key Idea**: Human vision tolerates slight warps and smoothed inputs if they improve compression (reducing blocking/artifacts).
* **Results**: Achieves \~7.4% reduction in bpp at equal PSNR/SSIM. In user studies, >50% of raters preferred the pre-edited JPEG over baseline at low bitrates.
* **Strengths/Limits**: Significantly reduces JPEG artifacts and preserves detail better. Limitation: computationally expensive on CPU (seconds per megapixel) and focused on JPEG; adapting to video codecs needs more work.

Slide 8: **Deep Perceptual Preprocessing (DPP) for Video (Chadha *et al.*, 2021)**

* **DPP** is a lightweight CNN preprocessor applied to each frame before any standard codec (tested on AVC, AV1, VVC).
* **Loss Function**: Multi-term: (a) full-reference L1+SSIM fidelity, (b) a no-reference IQA score (to improve perceptual quality), (c) an estimated codec bitrate loss from motion residuals.
* **Objective**: Enhance perceptually important features (edges/textures) and suppress compressible redundancy *before* encoding.
* **Results**: Across all metrics, DPP + H.264/VVC yields \~11% average bitrate reduction at equal perceived quality (11–15% BD-rate drop in some cases).
* **Strengths/Limits**: Codec-agnostic and encoder-only (no decoder changes). However, performance depends on training target bitrate; can occasionally introduce slight “crunchiness” if over-smoothing.

Slide 9: **Rate-Perception Preprocessor (RPP) (Ma *et al.*, 2023)**

* A light CNN with attention, optimizing both rate and perceptual quality.
* Introduces an **adaptive DCT loss**: penalizes spatial redundancy while keeping essential high-frequency content. Also uses MS-SSIM loss and high-order degradation augmentation.
* **Deployment**: Single-pass framewise preprocessing compatible with any standard encoder (AVC/HEVC/VVC/AV1) without changing bitstream.
* **Results**: \~16.3% average BD-rate savings under multiple metrics (vs. plain codec). In subjective tests, 87% of users rated RPP+codec output as equal or better quality, with \~12% average bit savings.
* **Strengths/Limits**: Very efficient (runs \~87 FPS on 1080p). Integrated into production (millions served). Limitation: benefits rely on training on representative content; very aggressive compression (very low QP) may still produce artifacts.

Slide 10: **Neural Pre-/Post-Processing (Khan *et al.*, 2025)**

* Proposes neural networks applied both **before** and **after** any standard codec (AV1, VVC) to maximize perceptual quality.
* Training uses a differentiable “codec simulator” that is pre-trained to mimic the target encoder’s rate-quality characteristics (two-phase training).
* **Results**: Achieves very large gains: e.g. for AV1, \~–20% BD-rate (improved SSIM/VMAF) on several datasets; for VVC, \~–15% (dataset-dependent).
* Qualitatively, the method better preserves structure and textures while denoising codec artifacts, yielding higher MOS.
* **Strengths/Limits**: Exploits full codec pipeline end-to-end. Limitations include complex two-phase training and added decode-side processing (post-net) which can add latency/cost.

Slide 11: **MDER: Multi-Dimensional Preprocessing (Wang *et al.*, 2024)**

* Tailored for end-to-end neural video codecs: network preprocesses frames to **enhance features and suppress irrelevant info** across spatial & channel dimensions.
* Key modules: *degradation compensation*, *residual CNN with residual learning*, *Dense Blocks*, and a **dense feature extraction** step to improve motion coding (DFVC).
* **Goal**: Remove encoding noise and recover high-frequency detail *before* neural compression.
* **Results**: Compared to the base neural encoder, MDER achieves a bit-rate reduction of 0.0714 Bpp at equal PSNR (and 0.0536 at equal MS-SSIM), a significant efficiency gain.
* **Strengths/Limits**: Quantifiable improvement for learned codecs; lightweight design. However, applicability is limited to neural encoders – not directly useful for standard codecs.

Slide 12: **Deformation-Aware Preprocessing (Shaham & Michaeli, 2018)**

* A classic method for *image* compression: allows slight geometric deformations of the input to improve compressibility.
* **Key idea**: Traditional distortion metrics (MSE, SSIM) are sensitive to small shifts; humans tolerate small misalignments.
* The algorithm finds a minor warp of the image that makes it more compressible (e.g. aligning textures), then compresses with any codec (JPEG, JP2, BPG, etc.).
* **Result**: At the same bit-rate, it preserves details that vanilla compression loses (see Figure 1 in \[3] for a 150:1 JPEG2000 example).
* **Strengths/Limits**: Plug-in (codec‐agnostic) and often imperceptible warping. Limitation: applicability is mostly for still images, and large deformations can be visible (hence method keeps deformations small).

Slide 13: **Preprocessing for Machine-Vision (Lu *et al.*, 2022)**

* Although focused on machine tasks, this highlights a related angle: using preprocessing to preserve semantic content (not HVS).
* Introduces a neural preprocessing (NPP) before a traditional codec (e.g., BPG) so that useful features for downstream tasks (detection/classification) are retained while irrelevant data is suppressed.
* Uses a differentiable proxy network to allow end-to-end training with non-differentiable codecs.
* **Result**: \~20% bitrate savings for the same machine task accuracy (object detection/classification).
* **Relevance**: Demonstrates the power of learned preprocessing – though optimizing for machines not humans. Insight: the framework (proxy codec, NPP) could be adapted for HVS objectives.

Slide 14: **Comparative Performance Summary**

* **Adaptive JND Filters (Vidal)**: \~19.3% bitrate reduction at constant perceived quality.
* **Image Pre-Editor (Talebi)**: \~7.4% Bpp reduction (JPEG, equal PSNR/SSIM), with >50% user preference in tests.
* **Deep Preprocessing (DPP)**: \~11% average BD-rate drop (AV1/H.264/VVC).
* **RPP**: \~16.3% BD-rate savings across AVC/HEVC/VVC (subjective 87% pref at 12% bitrate saving).
* **Neural Wrapper**: \~–20% BD-rate on AV1 and \~–15% on VVC, across SSIM/VMAF metrics.
* **MDER (neural)**: –0.0714 Bpp at equal PSNR.
* **Machine-task NPP**: \~20% bitrate reduction for equal detection/classification accuracy (for comparison).
* *In summary:* Learned CNN-based methods generally outperform classical filters in savings, especially on complex video; but JND-based filters are simple and still yield large gains.

Slide 15: **Research Gaps & Limitations**

* **Perceptual Models**: Current methods mostly use basic HVS models or image metrics. There is a gap in modeling higher-level perception: color perception, HDR, motion masking, and eye-movement effects.
* **Quality Metrics**: Reliance on SSIM/VMAF is imperfect (e.g. they ignore chroma or temporal quality). Better full-reference (and no-reference) perceptual metrics for video are needed.
* **Temporal Consistency**: Most preprocessors operate frame-wise. Temporal artifacts (flicker, motion inconsistency) can arise. Methods to ensure consistent multi-frame processing are underdeveloped.
* **Real-time and Devices**: Few works address very low-latency or mobile deployment. Lightweight models or hardware implementations (e.g. ASIC/FPGA) for preprocessor are needed.
* **Content Adaptivity**: Many solutions are trained on general datasets. Domain-specific preprocessing (animation vs natural scenes vs screen content) is still open. Preprocessors may misbehave on out-of-distribution content.

Slide 16: **Emerging Opportunities**

* **Saliency/Attention**: Integrating visual saliency or task-based attention to focus bits on important regions. E.g. weighting loss by saliency, or adaptive filtering that spares salient areas.
* **Semantic/Depth Cues**: Using semantic segmentation or depth maps to guide preprocessing (e.g. focus detail on faces or important objects). This multi-modal approach is relatively unexplored.
* **Generative Enhancement**: Combining preprocessing with generative models (GANs or diffusion). For instance, allow a preprocessor to remove fine texture, then let a decoder-side model hallucinate plausible detail, reducing bitrate further.
* **End-to-End Co-design**: Jointly optimizing preprocessing and codec (even hybrid learned-traditional) could yield better global solutions. Reinforcement learning or differentiable codec proxies may enable this.
* **Adaptive & Personalized Coding**: Preprocessors that adapt to viewer feedback, device (e.g. VR gaze direction for foveated filtering), or environmental context (network conditions) could enhance user experience.

Slide 17: **Novel Idea: Saliency-Weighted Preprocessing**

* **Concept**: Use a learned saliency map (or eye-tracking data) to weight the preprocessing strength: apply stronger smoothing/compression in low-saliency regions and gentle processing in salient regions.
* **Technical Approach**: Train a CNN that takes the input frame and a saliency mask, adjusting filter strength or encoding quality locally. Saliency can be predicted by existing models (e.g. neural saliency detectors) or eye-gaze hardware.
* **Expected Contribution**: More bitrate savings by aggressively compressing visually unimportant areas, while preserving critical details in attended regions. This aligns RD optimization with human attention.

Slide 18: **Novel Idea: Temporally-Adaptive Preprocessing**

* **Concept**: Incorporate motion information so that preprocessing varies with temporal context. For example, heavier smoothing during fast motion (when artifacts are masked) and lighter filtering in static shots.
* **Technical Approach**: Extend the preprocessing network to take two or more consecutive frames (or motion vectors) as input, and output temporally-coherent enhancements. Use temporal losses or recurrent modules.
* **Expected Contribution**: Improved consistency across frames (avoiding flicker), and exploiting motion masking to achieve bitrate savings that static-frame methods miss.

Slide 19: **Novel Idea: RL-Optimized Preprocessor**

* **Concept**: Frame the preprocessor as an agent trained with reinforcement learning. The agent’s action is to set filtering parameters or choose transformations per frame/block; the reward balances bitrate and perceptual quality (using a learned metric or feedback).
* **Technical Approach**: Define states (e.g. scene features, buffer state), actions (filter strength map, transform choices), and a perceptual reward (e.g. negative RD or a learned quality score). Train with deep RL (e.g. PPO).
* **Expected Contribution**: Content-aware, adaptive preprocessing policy that learns from experience. Could outperform static-trained CNNs by dynamically adjusting to each video’s characteristics and even user feedback.

Slide 20: **Novel Idea: Learned HVS Models for Loss Functions**

* **Concept**: Collect eye-tracking / subjective data on video compression artifacts to train a new full-reference perceptual loss. Use this to supervise the preprocessing network.
* **Technical Approach**: Record human gaze or quality ratings on compressed videos; train a CNN to predict perceptual error maps. Then use this as a differentiable loss (instead of SSIM/VMAF) during preprocessor training.
* **Expected Contribution**: A perceptual loss grounded in true human perception that can generalize across content (handling color, dynamic range, motion) better than hand-crafted metrics. Improves the alignment of optimization with actual viewer preferences.

Slide 21: **Novel Idea: Edge- and Texture-Preserving Multi-Scale Filtering**

* **Concept**: Design a preprocessor that explicitly preserves edges and textures by operating in a multi-scale or wavelet domain.
* **Technical Approach**: Decompose the frame into scales (e.g. Laplacian pyramid or wavelet bands). Apply strong smoothing only to low-detail bands and adaptive filtering to high-frequency bands, guided by an edge detector or texture classifier. Optionally refine with a CNN.
* **Expected Contribution**: Maximizes bitrate savings by filtering true redundancy while guaranteeing that sharp edges and fine textures are maintained. Provides more controllability than a single-resolution filter.

Slide 22: **Key Takeaways**

* **Substantial RD Gains:** Preprocessing consistently yields large bitrate reductions for equal perceived quality (often 10–20% BD-rate or more). This has been validated across JPEG, AVC/HEVC/VVC, and AV1.
* **Deep vs Classical:** Learned CNN preprocessors (DPP, RPP, neural wrapping) generally outperform classical filters by adapting to content. E.g. RPP’s \~16% saving vs. \~19% for JND filters. The best methods often combine both (e.g. DCT losses with HVS models).
* **Metric and Subjective Alignment:** Success is measured by multiple metrics and human studies. Many works report both SSIM/VMAF gains and user preference (e.g. Talebi’s user study). Balanced loss design is crucial.
* **Versatility:** Effective preprocessors are encoder-agnostic (plug into any codec) and do not alter the bitstream – a major practical advantage. They complement, rather than replace, existing coding pipelines.
* **Remaining Challenges:** HVS modeling and robust perceptual metrics remain imperfect, leaving room for innovation. Preprocessors must be tailored to video dynamics and diverse content.

Slide 23: **Future Research Directions**

* **Advanced HVS Models:** Incorporate color perception, HDR, and temporal vision models. For example, model chrominance masking or fixational eye movements in the loss function.
* **Real-Time Implementation:** Develop low-complexity DNNs or hardware accelerators for preprocessing to enable on-device or live-stream deployment.
* **Cross-Domain Optimization:** Explore joint optimization for *both* human and machine perception (e.g. A/V coding that serves humans *and* machine vision tasks).
* **Interactive/Adaptive Coding:** Leverage user feedback or QoE signals to adapt preprocessing over time (e.g. in streaming services with ABR feedback).
* **Multi-Criteria RD Optimization:** Extend beyond single-metric: e.g. optimize for perceptual quality *and* energy efficiency (“green” coding) by smart preprocessing.
