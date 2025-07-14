**Create a comprehensive PowerPoint presentation for a Perceptual Preprocessor POC Demo. Here are the complete technical details and requirements:**
**Project Overview:** I'm building a perceptual preprocessor and have developed a Proof of Concept (POC) with an Android app that processes photos through 4 different paths using AVC (Advanced Video Codec) for preprocessing.
**Technical Architecture & Challenges:**
**Core Problem:** AVC algorithm only supports YUV422 and YUV444 formats, but Android natively only supports YUV420 image reading (YUV422/444 reading is not supported).
**Processing Paths Breakdown:**
**Path 1:** Direct JPEG save
* Image reader → Direct JPEG format save
* Baseline/reference path
**Path 2:** Control path (mimics Path 3/4 without preprocessing)
* Image reader (YUV420) → Convert to NV21 → compressToJpeg() → Save
* Purpose: Ensures equal format comparison with preprocessed paths
**Path 3:** AVC preprocessing with NV21 output
* Image reader (YUV420) → Convert to buffer → Pass to native encode() function
* Native processing: Upscale YUV420 to YUV422 (quality degradation occurs here)
* AVC encode → AVC decode → Reconstruction (YUV422 format)
* Calculate PSNR between input buffer (after YUV422 conversion) and reconstruction
* Convert reconstruction (YUV422) to NV21 format → compressToJpeg() → Save
* Display PSNR as toast message
**Path 4:** AVC preprocessing with YUY2 output (Main comparison path)
* Same process as Path 3 until reconstruction
* Convert reconstruction (YUV422) to YUY2 format → compressToJpeg() → Save
* Note: YUY2 and reconstruction both use YUV422 format, making this the primary comparison path
**Key Technical Constraints:**
* compressToJpeg() only accepts NV21 and YUY2 formats
* Android's limitation on YUV422/444 image reading
* Quality degradation from YUV420 → YUV422 upscaling
* PSNR calculation between processed input and reconstruction
**Final Comparison:** Path 2 vs Path 4 for POC validation
**Presentation Requirements:** Create a professional PPT covering:
1. **Title & Executive Summary**
2. **Problem Statement** - Why perceptual preprocessing is needed
3. **Technical Challenges** - YUV format limitations and Android constraints
4. **System Architecture** - Visual diagram of all 4 paths
5. **Detailed Flow Diagrams** - Each path's processing steps
6. **AVC Integration** - How codec fits into preprocessing pipeline
7. **Demo Setup Instructions** - Step-by-step guide to run the app
8. **How to Reproduce Results** - Exact steps for testing
9. **Results Interpretation** - What the PSNR values mean
10. **Visualization Pages** - Screenshots and UI mockups of result displays
11. **Comparison Analysis** - Path 2 vs Path 4 results explanation
12. **Technical Limitations** - Current constraints and quality trade-offs
13. **Next Steps** - Future improvements and full implementation roadmap
14. **Q&A Preparation** - Common questions and technical deep-dives
**Visual Elements Needed:**
* Flow diagrams for each processing path
* Format conversion illustrations (YUV420→422, YUV422→NV21/YUY2)
* Android app UI mockups showing toast messages and saved images
* PSNR comparison charts
* Before/after image quality comparisons
**Target Audience:** Technical stakeholders who need to understand both the preprocessing concept and implementation details.
Make it comprehensive enough for both technical demos and executive presentations, with clear explanations of why each design decision was made given the platform constraints.
