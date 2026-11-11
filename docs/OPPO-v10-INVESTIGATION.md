# OPPO Find X9后续调查：v10

## v9反馈与结论

用户确认v9在OPPO Find X9上导入后，照片库预览一直转圈；点开照片仍闪退，系统提示“运行内存占用异常”。诊断表明问题发生于180×240预览，系统以LOW_MEMORY终止进程，记录的RSS约6.9GiB；从最后处理检查点到退出约3.9秒。这里只保留定位所需的汇总，不公开原始诊断。设备不在本地，未做OPPO真机复测；用户随后反馈v10恢复正常。

系统最后采样RSS约6.9GiB，并非精确退出峰值。报告中的heapBytes/heapFreeBytes在重启恢复报告时读取，不能解释为死亡进程的Java堆；v10增加heapSample说明。[Android ApplicationExitInfo.getRss 文档](https://developer.android.com/reference/android/app/ApplicationExitInfo#getRss())说明了采样边界。

## 已确认的Android构建缺陷

只查看原始CPU条件会漏掉实际发布行为。Android unfused-FMA overlay将check_math_mode中的CPU设备条件放宽为true，保留已有Dekker数学路径，却同时保留了probe_cpu_storage_range调用。这个原本仅供桌面CPU Vulkan后端使用的容量实验，按设备maxStorageBufferRange而非图像尺寸分配测试数组。便携重建脚本和旧研究overlay均有该问题。

探测令n=floor(maxStorageBufferRange/12)+1048576；host输入、GPU输入/输出、host读回与两个staging合计约84n字节。如果设备允许相应dispatch，声明范围1GiB可触发约7.1GiB分配（不同缓冲的生命周期和驱动驻留会影响RSS）。这与手机报告数量级吻合，属于有明确触发路径的高可信原因；仍没有这台手机的Vulkan能力和调用栈，不能声称已经在OPPO复现。

v10在Android构建overlay中精确移除该容量实验调用，保留设备声明的storage limit，不提高上限；源码helper自身也增加独立CPU设备保护。旧研究脚本改为精确替换整个数学gate，避免全局替换再次误伤保护。没有关闭FMA校验，没有改变胶片数学、Dekker选择或shader。执行native/scripts/check_storage_probe.py在旧源码失败、修正后通过，并通过便携脚本重建实际arm64核心。新的核心与Android原包shader逐字节比较结果随包记录。

## 已确认并修正的代码路径

1. 原照片库缩略图会解码240长边后调用完整胶片核心。该路径的普通Exception仅写日志，UI没有位图时始终显示进度圈。v10优先读取当前编辑的已保存效果缩略图；没有时只解码原图，不调用胶片核心。原图缩略图明确标“原图预览”，不写入效果缓存；从编辑器返回且有有效结果后更新为“效果预览”。裁切/调色配方仍独立保存。
2. 缩略图增加读取中/失败状态，只在任务运行时转圈。错误显示“读取失败”，可以点开查看；取消、删除和批量套用清理相应状态。此改动解决已确认的异常被吞后UI等待，不保证图片解码器或GPU驱动永远不会挂起。
3. 首次引擎初始化单独记为ENGINE，进入图片处理再记PREVIEW/EXPORT，避免把初始化故障统称为图片预览故障。
4. 解码与原生线程可能在切换照片时重叠。v9共用一份进行中记录，解码完成可能清掉原生记录。v10为两条路径分开保存checkpoint，错误报告仍共用本地出口。退出记录同时收集EXCESSIVE_RESOURCE_USAGE、OTHER、UNKNOWN及系统说明，保持中性，不将厂商资源限制或未知原因断言为OOM。

v10没有更换GPU后端、绕过数值校验、关闭颗粒或更改胶片材料。新照片默认值继续为强度100、轻颗粒约12.5、胶片曝光0；旧编辑保留。原尺寸导出策略不变。

## 内存研究边界

源码追踪确认：256行分条并不代表全流程只保留256行。颗粒等阶段仍处理整图，条带结果还会组装回完整plane；session保留整图输入和negative，输出包含RGBA16及Android位图。64-lane数学probe本身很小；错误地连带执行的CPU容量probe是另一个实验，两者必须区分。

Windows RTX5070Ti以当前片屿DLL独立进程运行16个条件：daily/natural × 长边240/960 × full/live × grain0/.25。全部成功；240×160渲染约20–31ms，960×640约77–98ms。960条件pool的frame_high_water_bytes为54558736，cached_negative_bytes为7372800；240条件分别3686416和460800。开颗粒使持久计数增加15384字节，没有提高该pool高水位。它不是全进程或驱动峰值，不能据此指认颗粒导致OPPO退出。两档尺寸均小于live上限，full/live不能减少输入。实验未经过Android与片屿CPU后期处理；不是手机兼容证明。

旧Windows实验没有应用Android overlay，故不触发这个新增确认的缺陷；桌面正常不能排除手机构建路径的问题。Vulkan提交仍使用同步queue wait，本轮没有为未证实的驱动问题添加超时或替代后端。

## 验证与剩余工作

新增Android测试覆盖：原图缩略图与解码像素一致、不写效果缓存、空文件失败后结束loading、解码完成不抹去原生checkpoint、编辑取消图库任务时清除状态。设备不在本地，本轮只编译这些测试，未执行。

主机测试、核心/应用构建、lint和APK签名/源码核对以候选目录的verification及checks为准。v10修正已确认的大缓冲探测缺陷与图库问题，用户随后反馈OPPO恢复正常，本地没有OPPO硬件验证。后续v12在Redmi K80 Pro执行21项针对性测试及最终Release交互检查，范围见[发布说明](RELEASE-v0.3.2.md)；此前v7真机结果与推广片不能替代此验证。
