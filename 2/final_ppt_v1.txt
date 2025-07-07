Here is structured content for the **first 3 slides** of your presentation on your **perceptual preprocessor for video codecs**, synthesized using your provided files (`Content.pdf` and `PPT_Content_1.md`):

---

### **📄 Slide 1: Problem Statement**

#### 🎯 **Goal**

To develop a **perception-aware video pre-processing module** that transforms input frames such that:

* The **compressed output** maintains **perceptual equivalence** to the original.
* The **compression ratio** improves by **25–30%**.
* The solution is **on-device compatible** and codec-agnostic.

#### ❗ **The Problem**

Traditional compression pipelines apply codecs like H.264/HEVC after capturing raw frames. These often:

* Introduce **visual artifacts** (blocking, blur, ringing).
* Fail to prioritize **perceptually important** information.
* Rely on **post-processing**, which cannot recover lost details.

We aim to shift this paradigm to **intelligent pre-processing**: clean or adapt the input before compression to **preserve visual quality** while achieving **lower bitrate**.

#### 📱 **Example Use Case**

* Mobile video recording: A user captures a 4K video on a smartphone.
* Instead of compressing raw pixels, our **pre-processor** first adapts frames based on **human visual sensitivity**.
* Result: Visually indistinguishable video at **significantly lower file size**, ideal for storage and sharing.

---

### **📄 Slide 2: Introduction & Background**

#### 🌍 **Domain Overview**

* **Video Compression** is essential for streaming, recording, and storage—over **80% of internet traffic** is video.
* Standard codecs (H.264, H.265, AV1) use **handcrafted algorithms** and optimize **rate-distortion tradeoff**, often neglecting perceptual quality.

#### 👁️ **Why Perception Matters**

* The **Human Visual System (HVS)** doesn't perceive all information equally:

  * Sensitive to **edges, faces, textures**, and less to **high-frequency noise** or **peripheral blur**.
  * **Masking effects** allow some distortions to go unnoticed.
* Leveraging HVS in preprocessing allows removal of **perceptually redundant data**, enabling **higher compression without visible quality loss**.

#### 🧠 **Why Now?**

* Advances in **deep learning**, **no-reference IQA**, and mobile compute make it feasible to:

  * Design smart pre-processors.
  * **Deploy them on-device** for real-time applications.

#### 🔍 **Importance of the Problem**

* Reduces bandwidth/storage in consumer devices (e.g., Samsung smartphones).
* Sets the foundation for **next-gen compression pipelines**.
* Offers a **strategic edge** in mobile imaging solutions.

---

### **📄 Slide 3: Related Work & Tools**

#### 📚 **Literature Survey**

* **Chadha et al. (2021):** DPP using L1+SSIM and no-ref IQA loss; \~11% BD-rate savings.
* **Ma et al. (2023):** RPP with adaptive DCT loss and attention; \~16% bitrate saving, high subjective quality.
* **Vidal et al. (2021):** BilAWA filter using JND models; \~19.3% savings with AVC.
* **Talebi et al. (2021):** PEN for JPEG with smoothing & warps; visually better compression.
* **Khan et al. (2025):** Neural wrapper using codec simulator; \~20% BD-rate drop for AV1.

#### 🛠️ **Tools & Frameworks Used**

* **Deep Learning Models:** CNN-based perceptual filters trained using PSNR, SSIM, VMAF, and perceptual loss functions.
* **Codec Proxies:** Simulated differentiable codecs for backpropagation during training.
* **Metrics Used:**

  * **Objective:** PSNR, SSIM, MS-SSIM, VMAF.
  * **Subjective:** Human preference studies, MOS.
* **On-device Implementation:**

  * Android **Camera2 API** and **Java/Kotlin** for native integration.
  * Optimization for **mobile runtime performance**.

#### 🧪 **Pre-requisite Research**

* Studied **HVS models**: foveation, masking, temporal sensitivity.
* Benchmarked standard and deep-learning based **IQA metrics**.
* Evaluated multiple video sequences across content types for robustness.

---

Would you like me to generate visuals or speaker notes for these slides as well?
