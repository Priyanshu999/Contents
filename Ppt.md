I'll provide you with content for three PPT slides and create an SVG diagram showing all 4 processing paths.

## Slide 1: System Overview & Architecture

**Title: Perceptual Preprocessor POC - System Architecture**

• **Core System**: Android app with 4 processing paths for perceptual preprocessing validation
• **Primary Challenge**: AVC algorithm requires YUV422/444 but Android only supports YUV420 reading
• **Processing Pipeline**: Image capture → Format conversion → AVC preprocessing → Quality assessment
• **Key Components**: Native AVC encode/decode functions, format converters, PSNR calculator
• **Output Formats**: JPEG (Path 1), NV21 (Paths 2,3), YUY2 (Path 4)
• **Validation Method**: PSNR calculation between input buffer and reconstruction

## Slide 2: Processing Paths & Problem Resolution

**Title: Four-Path Processing Strategy & Solutions Implemented**

• **Path 1 (Baseline)**: Direct JPEG save for reference comparison
• **Path 2 (Control)**: YUV420 → NV21 → JPEG pipeline without preprocessing
• **Path 3 (AVC-NV21)**: YUV420 → YUV422 upscaling → AVC processing → NV21 output
• **Path 4 (AVC-YUY2)**: Same as Path 3 but YUY2 output for optimal format matching
• **Key Resolution**: Implemented YUV420 to YUV422 upscaling to bridge format gap
• **Quality Tracking**: Real-time PSNR calculation and toast display
• **Final Comparison**: Path 2 vs Path 4 validates preprocessing effectiveness

## Slide 3: Deep Learning Model Development

**Title: Perceptual Preprocessor Model - Deep Learning Implementation**

• **Model Architecture**: Deep learning preprocessor following Chadha's paper methodology
• **Perceptual Loss**: LIQE (no-reference) instead of custom CNN for better generalization
• **Dataset Advantage**: LIQE trained on diverse data superior to Koniq-specific training
• **Preprocessing Module**: Dilated convolutions for enhanced feature extraction
• **Loss Function Components**: MS-SSIM + PSNR (detailed loss) + LIQE (perceptual loss) + Rate loss
• **Virtual Codec**: Custom implementation mimicking standard codec behavior
• **Current Status**: Work in progress - model training and optimization ongoing

Now, let me create the SVG diagram showing all 4 processing paths:

<svg viewBox="0 0 1200 800" xmlns="http://www.w3.org/2000/svg">
  <!-- Background -->
  <rect width="1200" height="800" fill="#f8f9fa"/>
  
  <!-- Title -->
  <text x="600" y="30" text-anchor="middle" font-size="24" font-weight="bold" fill="#2c3e50">
    Perceptual Preprocessor - Four Processing Paths
  </text>
  
  <!-- Common starting point -->
  <rect x="50" y="70" width="120" height="40" fill="#3498db" stroke="#2980b9" stroke-width="2" rx="5"/>
  <text x="110" y="95" text-anchor="middle" font-size="12" fill="white" font-weight="bold">Image Reader</text>
  <text x="110" y="125" text-anchor="middle" font-size="10" fill="#34495e">(YUV420)</text>
  
  <!-- Path 1: Direct JPEG -->
  <g id="path1">
    <text x="250" y="85" font-size="14" font-weight="bold" fill="#e74c3c">Path 1: Direct JPEG</text>
    <rect x="230" y="100" width="100" height="30" fill="#e74c3c" stroke="#c0392b" stroke-width="2" rx="3"/>
    <text x="280" y="120" text-anchor="middle" font-size="10" fill="white">Direct JPEG Save</text>
    <rect x="230" y="150" width="100" height="30" fill="#95a5a6" stroke="#7f8c8d" stroke-width="2" rx="3"/>
    <text x="280" y="170" text-anchor="middle" font-size="10" fill="white">Output: JPEG</text>
  </g>
  
  <!-- Arrow from Image Reader to Path 1 -->
  <line x1="170" y1="90" x2="230" y2="115" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  
  <!-- Path 2: Control -->
  <g id="path2">
    <text x="400" y="85" font-size="14" font-weight="bold" fill="#f39c12">Path 2: Control</text>
    <rect x="380" y="100" width="80" height="30" fill="#f39c12" stroke="#e67e22" stroke-width="2" rx="3"/>
    <text x="420" y="120" text-anchor="middle" font-size="10" fill="white">YUV420→NV21</text>
    <rect x="380" y="150" width="80" height="30" fill="#f39c12" stroke="#e67e22" stroke-width="2" rx="3"/>
    <text x="420" y="170" text-anchor="middle" font-size="10" fill="white">compressToJpeg()</text>
    <rect x="380" y="200" width="80" height="30" fill="#95a5a6" stroke="#7f8c8d" stroke-width="2" rx="3"/>
    <text x="420" y="220" text-anchor="middle" font-size="10" fill="white">Output: JPEG</text>
  </g>
  
  <!-- Arrow from Image Reader to Path 2 -->
  <line x1="170" y1="90" x2="380" y2="115" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  
  <!-- Path 3: AVC-NV21 -->
  <g id="path3">
    <text x="580" y="85" font-size="14" font-weight="bold" fill="#27ae60">Path 3: AVC-NV21</text>
    <rect x="550" y="100" width="90" height="30" fill="#27ae60" stroke="#229954" stroke-width="2" rx="3"/>
    <text x="595" y="120" text-anchor="middle" font-size="9" fill="white">YUV420→Buffer</text>
    <rect x="550" y="150" width="90" height="30" fill="#27ae60" stroke="#229954" stroke-width="2" rx="3"/>
    <text x="595" y="170" text-anchor="middle" font-size="9" fill="white">Upscale→YUV422</text>
    <rect x="550" y="200" width="90" height="30" fill="#8e44ad" stroke="#7d3c98" stroke-width="2" rx="3"/>
    <text x="595" y="220" text-anchor="middle" font-size="9" fill="white">AVC Encode</text>
    <rect x="550" y="250" width="90" height="30" fill="#8e44ad" stroke="#7d3c98" stroke-width="2" rx="3"/>
    <text x="595" y="270" text-anchor="middle" font-size="9" fill="white">AVC Decode</text>
    <rect x="550" y="300" width="90" height="30" fill="#e67e22" stroke="#d35400" stroke-width="2" rx="3"/>
    <text x="595" y="320" text-anchor="middle" font-size="9" fill="white">PSNR Calc</text>
    <rect x="550" y="350" width="90" height="30" fill="#27ae60" stroke="#229954" stroke-width="2" rx="3"/>
    <text x="595" y="370" text-anchor="middle" font-size="9" fill="white">YUV422→NV21</text>
    <rect x="550" y="400" width="90" height="30" fill="#27ae60" stroke="#229954" stroke-width="2" rx="3"/>
    <text x="595" y="420" text-anchor="middle" font-size="9" fill="white">compressToJpeg()</text>
    <rect x="550" y="450" width="90" height="30" fill="#95a5a6" stroke="#7f8c8d" stroke-width="2" rx="3"/>
    <text x="595" y="470" text-anchor="middle" font-size="9" fill="white">Output: JPEG</text>
  </g>
  
  <!-- Arrow from Image Reader to Path 3 -->
  <line x1="170" y1="90" x2="550" y2="115" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  
  <!-- Path 4: AVC-YUY2 -->
  <g id="path4">
    <text x="780" y="85" font-size="14" font-weight="bold" fill="#9b59b6">Path 4: AVC-YUY2</text>
    <rect x="750" y="100" width="90" height="30" fill="#9b59b6" stroke="#8e44ad" stroke-width="2" rx="3"/>
    <text x="795" y="120" text-anchor="middle" font-size="9" fill="white">YUV420→Buffer</text>
    <rect x="750" y="150" width="90" height="30" fill="#9b59b6" stroke="#8e44ad" stroke-width="2" rx="3"/>
    <text x="795" y="170" text-anchor="middle" font-size="9" fill="white">Upscale→YUV422</text>
    <rect x="750" y="200" width="90" height="30" fill="#8e44ad" stroke="#7d3c98" stroke-width="2" rx="3"/>
    <text x="795" y="220" text-anchor="middle" font-size="9" fill="white">AVC Encode</text>
    <rect x="750" y="250" width="90" height="30" fill="#8e44ad" stroke="#7d3c98" stroke-width="2" rx="3"/>
    <text x="795" y="270" text-anchor="middle" font-size="9" fill="white">AVC Decode</text>
    <rect x="750" y="300" width="90" height="30" fill="#e67e22" stroke="#d35400" stroke-width="2" rx="3"/>
    <text x="795" y="320" text-anchor="middle" font-size="9" fill="white">PSNR Calc</text>
    <rect x="750" y="350" width="90" height="30" fill="#9b59b6" stroke="#8e44ad" stroke-width="2" rx="3"/>
    <text x="795" y="370" text-anchor="middle" font-size="9" fill="white">YUV422→YUY2</text>
    <rect x="750" y="400" width="90" height="30" fill="#9b59b6" stroke="#8e44ad" stroke-width="2" rx="3"/>
    <text x="795" y="420" text-anchor="middle" font-size="9" fill="white">compressToJpeg()</text>
    <rect x="750" y="450" width="90" height="30" fill="#95a5a6" stroke="#7f8c8d" stroke-width="2" rx="3"/>
    <text x="795" y="470" text-anchor="middle" font-size="9" fill="white">Output: JPEG</text>
  </g>
  
  <!-- Arrow from Image Reader to Path 4 -->
  <line x1="170" y1="90" x2="750" y2="115" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  
  <!-- Connecting lines within paths -->
  <!-- Path 2 connections -->
  <line x1="420" y1="130" x2="420" y2="150" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="420" y1="180" x2="420" y2="200" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  
  <!-- Path 3 connections -->
  <line x1="595" y1="130" x2="595" y2="150" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="595" y1="180" x2="595" y2="200" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="595" y1="230" x2="595" y2="250" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="595" y1="280" x2="595" y2="300" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="595" y1="330" x2="595" y2="350" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="595" y1="380" x2="595" y2="400" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="595" y1="430" x2="595" y2="450" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  
  <!-- Path 4 connections -->
  <line x1="795" y1="130" x2="795" y2="150" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="795" y1="180" x2="795" y2="200" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="795" y1="230" x2="795" y2="250" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="795" y1="280" x2="795" y2="300" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="795" y1="330" x2="795" y2="350" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="795" y1="380" x2="795" y2="400" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  <line x1="795" y1="430" x2="795" y2="450" stroke="#34495e" stroke-width="2" marker-end="url(#arrowhead)"/>
  
  <!-- Comparison annotation -->
  <rect x="900" y="200" width="250" height="120" fill="#ecf0f1" stroke="#bdc3c7" stroke-width="2" rx="5"/>
  <text x="1025" y="225" text-anchor="middle" font-size="16" font-weight="bold" fill="#2c3e50">Key Comparisons</text>
  <text x="920" y="245" font-size="12" fill="#34495e">• Path 2 vs Path 4: POC validation</text>
  <text x="920" y="265" font-size="12" fill="#34495e">• YUV422 format matching in Path 4</text>
  <text x="920" y="285" font-size="12" fill="#34495e">• PSNR tracking in Paths 3 & 4</text>
  <text x="920" y="305" font-size="12" fill="#34495e">• Quality degradation at upscaling</text>
  
  <!-- Technical constraints box -->
  <rect x="900" y="350" width="250" height="100" fill="#ffeaa7" stroke="#fdcb6e" stroke-width="2" rx="5"/>
  <text x="1025" y="375" text-anchor="middle" font-size="16" font-weight="bold" fill="#2c3e50">Technical Constraints</text>
  <text x="920" y="395" font-size="12" fill="#34495e">• Android: YUV420 reading only</text>
  <text x="920" y="415" font-size="12" fill="#34495e">• AVC: YUV422/444 required</text>
  <text x="920" y="435" font-size="12" fill="#34495e">• compressToJpeg: NV21/YUY2 only</text>
  
  <!-- Toast message indication -->
  <rect x="680" y="300" width="60" height="20" fill="#ff6b6b" stroke="#e55656" stroke-width="1" rx="10"/>
  <text x="710" y="315" text-anchor="middle" font-size="9" fill="white">Toast PSNR</text>
  <line x1="660" y1="310" x2="680" y2="310" stroke="#e55656" stroke-width="1" stroke-dasharray="3,3"/>
  
  <!-- Legend -->
  <rect x="50" y="550" width="400" height="200" fill="#ffffff" stroke="#bdc3c7" stroke-width="2" rx="5"/>
  <text x="250" y="575" text-anchor="middle" font-size="16" font-weight="bold" fill="#2c3e50">Legend</text>
  <rect x="70" y="590" width="15" height="15" fill="#3498db"/>
  <text x="95" y="602" font-size="12" fill="#34495e">Input/Common Processing</text>
  <rect x="70" y="615" width="15" height="15" fill="#e74c3c"/>
  <text x="95" y="627" font-size="12" fill="#34495e">Path 1: Direct JPEG</text>
  <rect x="70" y="640" width="15" height="15" fill="#f39c12"/>
  <text x="95" y="652" font-size="12" fill="#34495e">Path 2: Control</text>
  <rect x="70" y="665" width="15" height="15" fill="#27ae60"/>
  <text x="95" y="677" font-size="12" fill="#34495e">Path 3: AVC-NV21</text>
  <rect x="70" y="690" width="15" height="15" fill="#9b59b6"/>
  <text x="95" y="702" font-size="12" fill="#34495e">Path 4: AVC-YUY2</text>
  <rect x="70" y="715" width="15" height="15" fill="#8e44ad"/>
  <text x="95" y="727" font-size="12" fill="#34495e">AVC Processing</text>
  <rect x="250" y="590" width="15" height="15" fill="#e67e22"/>
  <text x="275" y="602" font-size="12" fill="#34495e">PSNR Calculation</text>
  <rect x="250" y="615" width="15" height="15" fill="#95a5a6"/>
  <text x="275" y="627" font-size="12" fill="#34495e">Final Output</text>
  
  <!-- Arrow marker definition -->
  <defs>
    <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto">
      <polygon points="0 0, 10 3.5, 0 7" fill="#34495e"/>
    </marker>
  </defs>
  
  <!-- Format indicators -->
  <text x="110" y="520" text-anchor="middle" font-size="12" font-weight="bold" fill="#2c3e50">Format Flow:</text>
  <text x="110" y="540" text-anchor="middle" font-size="10" fill="#34495e">YUV420 → YUV422 → NV21/YUY2</text>
</svg>

The content above provides you with three concise PPT slides following best practices (bullet points, clear structure, technical focus) and an comprehensive SVG diagram that visualizes all four processing paths in a single view. The diagram shows:

1. **Common starting point**: Image Reader (YUV420)
2. **Path branching**: Four distinct processing routes
3. **Technical constraints**: Highlighted in yellow box
4. **Key comparisons**: Shown in gray box
5. **Color-coded legend**: For easy identification
6. **Format flow**: Shows the critical format conversions
7. **PSNR tracking**: Indicated with toast notifications

The diagram effectively communicates the technical complexity of bridging the format gap between Android's YUV420 limitation and AVC's YUV422 requirement, while showing how each path addresses this challenge differently.
