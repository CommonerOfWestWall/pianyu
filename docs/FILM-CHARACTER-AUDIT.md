# 0.3.2：胶片性格与可复现配方

目标是保留上游胶片材料模型的色彩科学，用默认风格和少量微调贴近选定参考。偏色可以是性格；不把所有风格统一修成自然、讨喜的颜色，也不以拉大差别代替准确性。

## 怎样判断“像”

1. 核对胶片型号、摄影者声明、曝光、显影和扫描信息。相机、年代或图库分类本身不能证明胶片型号。
2. 看不同摄影者和场景反复出现的色彩关系、反差、暗部、高光、颗粒与光晕。过欠曝也可以研究；不能因为有偏色就排除。过程不明或特殊处理的照片单列，避免当作普通冲扫基准。
3. 默认值跨场景检查；特定参考另用手动配方贴近。单张参考的色相不能直接成为所有照片的规律。
4. 同输入比较锁定强度、构图与显示尺寸。异场景参考用于风格分析，不能计算整图色差当作模拟精度。
5. 公布参考来源、AI输入标识、实际 Android 输出、累计参数和哈希；不额外加工应用输出。

## 本轮证据与边界

以联系表视觉初筛的 Commons 元数据候选为 **166 张**：Portra 24、Vision3 24、800T 24、Provia 24、Velvia 22、Kodachrome 24、Acros II 24。这不是 166 张确认正常冲扫的定标片，更不是 1800 张独立实拍。

Acros II 的 24 张均来自同一位摄影者，不能据此宣称跨作者共识。旧 800T 主参考明确欠曝并重扫；旧 Velvia 主参考使用 Cs6 Creative Slide 工艺。二者可研究，但不能称为对应胶片的普通基准。

新增参考实验：Provia 科托尔湾（作者注明 Nikon Coolscan V ED）、Velvia 100 玫瑰（冲扫未知）、1985 年 Kodachrome 64 教堂（2005 年扫描）。AI生成类似题材的普通手机摄影风格输入，并非手机实拍、同机位拍摄或参考照片的逆变换。

**本轮不是严格色彩标定。** 尚无同光源、同场景数字/胶片成对曝光、色卡、扫描校准与独立盲测。参考的冲扫与AI输入的光照、材质不同，当前可支持可复现的风格探索，不能证明所有场景准确复刻。

## 七种材料与当前配方

| 入口 | 材料路径 | 观察重点与边界 |
| --- | --- | --- |
| 日常暖色 | Portra 400 + Endura | 肤色与负片/相纸响应；微暖、柔化是片屿额外配方，不定义所有 Portra。 |
| 电影日光 | Vision3 250D + 2383 | 电影负片与印片的层次；不是强行橙青或暗部必绿。 |
| 夜色光晕 | 500T 派生 800T + 2383 | 钨光平衡、冷暖与强光光晕；并非独立测量的 800T。 |
| 自然正片 | Provia 100F 正片扫描 | 青蓝和中间调层次；不是全局降饱和。 |
| 浓彩风光 | Velvia **100** 正片扫描 | 高彩度、绿蓝与浓密层次；不是 Velvia 50 或 100F。 |
| 克制复古 | Kodachrome 64 正片扫描 | 红蓝关系、绿色收敛；不把泛黄褪色当成必备特征。 |
| 柔和黑白 | Acros II + Ilford RC | 灰阶与纹理；柔化是额外配方，不表示所有 Acros II 都低反差。 |

材料 profile 未因这些参考而修改。`LookFinish.kt` 的额外参数单独公开；风格说明可关闭片屿预设，保留材料模型供用户创作，支持撤销。旧编辑保持原来路径；重新选择风格恢复当前预设。

真实手机补充：两张 Commons 公开 JPEG 的 EXIF 为 Xiaomi / Xiaomi 14 Pro（另一个写入型号代码 23116PN5BC），原作者均为 JacobLyu、CC BY-SA 4.0。使用应用同一 `PhotoIO.decode` 转 sRGB，诊断长边 1536 后分别生成七款默认、仅模型与手调，共 18 张实际安卓输出；没有把 JPG 先做成“RAW”或额外调色。公开文件也可能经过作者处理，不能称为经证明的相机原始直出，更不能代表多种手机。

进一步源代码核对及 JPG/RAW 路径区别见 [上游审查](UPSTREAM-REVIEW-2026-11.md)。当前曝光滑块作用在成片后；后续还需研究进入胶片模型前的曝光/曲线控制，不能只靠成片 HSL 代替上游流程。

## 一手资料

- [Kodak Portra 400](https://imaging.kodakalaris.com/sites/default/files/files/resources/e4050_portra_400.pdf)。
- [Kodak Vision3 250D，2018](https://www.kodak.com/content/products-brochures/Film/VISION-250D-Sellsheet_US_4PG-180829-SP_EN.pdf)：不静默套用后来 AHU 版本。
- [Fujifilm Provia 100F 实体胶片](https://asset.fujifilm.com/master/emea/files/2020-10/2c27854d5609945fbe7e48afc61f815d/films_provia-100f_datasheet_01.pdf)。
- [Fujifilm Velvia 100 实体胶片](https://asset.fujifilm.com/www/us/files/2020-03/226db49cde8b549d243c6498a80479cc/Velvia100_Brochure_Final.pdf)。
- [Kodak K64 E88，2005 原厂文件镜像](https://125px.com/docs/film/kodak/e88-2005_09.pdf)。
- [Fujifilm Acros II 135](https://asset.fujifilm.com/www/us/files/2020-08/73080443d0289b1a7b116add14a9bdfc/films_neopan100acros2_135_01_0.pdf)。
- [CineStill 800T 光源说明](https://help.cinestillfilm.com/hc/en-us/articles/360028874452-What-type-of-light-is-CineStill-800Tungsten-made-for)。

厂商资料约束解释，实际输出与用户复现检验效果；二者不能互相替代。
