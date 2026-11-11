"""Check the actual Android source transformations without allocating probe buffers."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
source = (root / "engine/src/gpu/vulkan_gpu.cpp").read_text(encoding="utf-8-sig")
probe = source.split("void probe_cpu_storage_range(std::string& detail) {", 1)[1]
probe = probe.split("void begin_frame()", 1)[0]
guard = "if (properties_.deviceType != VK_PHYSICAL_DEVICE_TYPE_CPU) return;"
assert guard in probe and probe.index(guard) < probe.index("std::vector<float> input"), "non-CPU device can enter the large storage probe"

# Execute the literal replacements declared by the portable build script.
script = (root / "docs/build-engine.ps1").read_text(encoding="utf-8-sig")
pairs = re.findall(r"Replace-Exactly \$gpu '([^']*)' '([^']*)'", script)
assert len(pairs) == 3, "expected gate, capability text, and desktop probe removal"
android = source
for old, new in pairs:
    assert android.count(old) == 1, f"replacement no longer unique: {old}"
    android = android.replace(old, new)
assert "if (zero && !exact && true /* verified Android unfused GPU */)" in android
assert guard in android, "Android transform removed device guard"
assert "probe_cpu_storage_range(detail);" not in android, "Android still calls the desktop capacity probe"
legacy = (root / "native/scripts/build-android-overlay.ps1").read_text(encoding="utf-8-sig")
assert "$gate = 'if (zero && !exact && properties_.deviceType == VK_PHYSICAL_DEVICE_TYPE_CPU) {'" in legacy
assert "$text.Replace($gate,'if (zero && !exact && true /* verified Android unfused GPU */) {')" in legacy
assert "$text.Replace('probe_cpu_storage_range(detail);','/* Android retains the advertised storage limit; no desktop capacity probe. */')" in legacy
patch = (root / "native/scripts/android-unfused-fma.patch").read_text(encoding="utf-8-sig")
assert "probe_cpu_storage_range(detail);" not in patch, "documented Android overlay still calls the capacity probe"
print("PASS: GPU guard precedes allocation; Android accepts unfused math without desktop capacity probing")
