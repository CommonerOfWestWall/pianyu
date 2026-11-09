"""Compare Android and desktop RGBA16 fixtures and write a viewable PNG."""
from pathlib import Path
import json, argparse
import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--android',type=Path,default=HERE/'results/android_1024.rgba16'); ap.add_argument('--desktop',type=Path,default=HERE/'samples/kodim01_1024.rgba16'); ap.add_argument('--out',type=Path,default=HERE/'results/android_1024.png'); ap.add_argument('--report',type=Path,default=HERE/'results/compare_1024.json'); a=ap.parse_args()
    x=np.fromfile(a.android,dtype='<u2'); y=np.fromfile(a.desktop,dtype='<u2')
    if x.size != y.size or x.size % 4: raise SystemExit(f'shape mismatch {x.size} {y.size}')
    x=x.reshape((-1,4)); y=y.reshape((-1,4)); d=np.abs(x.astype(np.int32)-y.astype(np.int32));
    # Width/height come from the actual driver report, never inferred from a flat buffer.
    metadata = dict(line.split('=', 1) for line in a.android.with_suffix('.txt').read_text().splitlines() if '=' in line)
    width, height = int(metadata['width']), int(metadata['height'])
    stride = int(metadata['row_stride_px'])
    rgba = x.reshape((height, stride, 4))[:, :width]
    Image.fromarray(((rgba[:, :, :3].astype(np.uint32) + 128) // 257).astype(np.uint8)).save(a.out)
    r={'values':int(x.size),'mae_u16':float(d.mean()),'max_abs_u16':int(d.max()),'android_nonzero':int(np.count_nonzero(x)),'android_min':int(x.min()),'android_max':int(x.max()),'android_std_u16':float(x.astype(np.float64).std()),'desktop_nonzero':int(np.count_nonzero(y))}
    a.report.write_text(json.dumps(r,indent=2)+'\n'); print(json.dumps(r))
if __name__=='__main__': main()
