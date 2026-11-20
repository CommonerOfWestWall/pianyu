package studio.pianyu.app

internal data class LookGuide(
    val source: String,
    val character: String,
    val scenes: String,
    val tags: Set<LookSort>,
)

internal val Look.guidance: LookGuide get() = LookGuides.getValue(id)

private val LookGuides = mapOf(
    "daily" to LookGuide(
        "以 Kodak Portra 400 负片和 Kodak Portra Endura 相纸模型为底。片屿在这个组合上微暖、抬暗部并压低反差。",
        "肤色和中间调偏暖，深色衣物与头发保留柔和层次，整体饱和度略收。",
        "适合日常人像、窗边生活照和街头记录。暖灯下白墙已经发黄时，先降强度，或稍降色温。",
        setOf(LookSort.WARM, LookSort.DAYLIGHT, LookSort.SPRING, LookSort.AUTUMN),
    ),
    "cinema" to LookGuide(
        "参考 Kodak Vision3 250D 日光电影负片与 Kodak 2383 印片模型。片屿收一点饱和度和高光，给暗部少量暖色。",
        "有方向的光会拉开建筑立面和街景的明暗，中间调相对克制，亮面过渡较柔。",
        "适合日光街景、旅行和侧光建筑。阴影里有主要人物时，先用较低强度看脸部层次。",
        setOf(LookSort.WARM, LookSort.DAYLIGHT, LookSort.SUMMER, LookSort.AUTUMN),
    ),
    "night" to LookGuide(
        "CineStill 800T 的材料参考采用基于 Kodak Vision3 500T 的衍生模型，再搭配 Kodak 2383 印片并启用高光光晕。",
        "发亮的灯边可能出现暖红晕，远离亮源的暗部轻抬，保留夜景里的冷暖混合。",
        "适合街灯、霓虹、招牌和夜间室内。带有亮点的画面更容易看出光晕，灯牌过亮时可以收低强度。",
        setOf(LookSort.COOL, LookSort.ARTIFICIAL, LookSort.WINTER),
    ),
    "natural" to LookGuide(
        "参考 Fujifilm Provia 100F 正片直扫模型。片屿略柔化反差并微调青蓝。",
        "青蓝清爽、整体色彩均衡。阴影稍提，浅色物件和天空有更柔和的过渡。",
        "适合旅行、自然光街拍和希望保留现场色彩的风景。薄雾或阴天也能用，原有天气气氛会参与塑造效果。",
        setOf(LookSort.COOL, LookSort.DAYLIGHT, LookSort.SPRING, LookSort.SUMMER),
    ),
    "landscape" to LookGuide(
        "参考 Fujifilm Velvia 100 正片直扫模型。片屿加强绿蓝的层次，同时限制红橙增益。",
        "绿叶和天空更浓，明暗层次较鲜明，亮部受到约束。彩度会使原有颜色更醒目。",
        "适合晴日风光、植物、草地和湖面。混有大面积肤色或红色主体时，先把强度调低。",
        setOf(LookSort.COOL, LookSort.DAYLIGHT, LookSort.SUMMER, LookSort.SPRING),
    ),
    "retro" to LookGuide(
        "参考 Kodak Kodachrome 64 正片直扫模型。片屿保留红色与深蓝、收敛绿色并轻加反差。",
        "红色标识和深蓝衣物更突出，绿色较克制。色彩反差能给街头物件带出偏干净的复古气氛。",
        "适合街头、旧招牌、旅行和日常静物。白墙或肤色已有明显偏色时，可以用较低强度。",
        setOf(LookSort.WARM, LookSort.DAYLIGHT, LookSort.AUTUMN, LookSort.SUMMER),
    ),
    "softmono" to LookGuide(
        "以 Fujifilm Neopan Acros 100 II 黑白负片和 Ilford Multigrade IV RC 相纸模型为底。片屿柔化反差并抬起暗部。",
        "重点是灰阶与纹理。暗部带柔和过渡，推荐强度 75 呈现完整黑白，调低强度会混回部分原色。",
        "适合靠光影构图的人像、建筑、纹理和阴天。可先观察主体与背景在灰阶里能否分开。",
        setOf(LookSort.DAYLIGHT, LookSort.WINTER, LookSort.AUTUMN),
    ),
    "mist-coast" to LookGuide(
        "片屿从样片对照里提炼浅亮海岸色，以 Provia 100F 正片直扫模型为底。",
        "青蓝偏浅，饱和与反差收低，暗处抬起，整体接近柔亮的浅色风景。",
        "适合海边、浅色建筑、开阔天空和晴日街景。想保留浓厚海水色时，可以用较低强度。",
        setOf(LookSort.COOL, LookSort.DAYLIGHT, LookSort.SPRING, LookSort.SUMMER),
    ),
    "soft-daylight" to LookGuide(
        "片屿在样片对照中提炼窗边柔光的处理方法，材料以 Portra 400 负片和 Portra Endura 相纸模型为底。",
        "肤色与中间调更明亮，亮面反差柔和，浅色衣物保留微暖的光感。",
        "适合窗边人像、阴影下的自然光和浅色衣物。白衣和皮肤本来很亮时，从较低强度试起。",
        setOf(LookSort.WARM, LookSort.DAYLIGHT, LookSort.SPRING),
    ),
    "olive-story" to LookGuide(
        "从片屿的旧建筑与林地配色对照提炼，以 Vision3 250D 负片与 Kodak 2383 印片模型为底。",
        "黄绿往橄榄移，暖色主体更明显，黑位偏深。树叶、砖墙和棕色材质放在一起更容易形成层次。",
        "适合林间、旧建筑、雨后街头和棕色材质。暗处有关键细节时，降低强度会更稳。",
        setOf(LookSort.WARM, LookSort.DAYLIGHT, LookSort.AUTUMN, LookSort.WINTER),
    ),
    "cyan-travel" to LookGuide(
        "片屿从旅行水景的样片对照提炼深青方向，以 Provia 100F 正片直扫模型为底。",
        "加深水面和天空的青蓝色，山石与草木保持克制，让冷色水面更突出。",
        "适合海景、湖面与有明显水色的旅行照。蓝色衣物也是这个色区，可按主体颜色降低强度。",
        setOf(LookSort.COOL, LookSort.DAYLIGHT, LookSort.SUMMER, LookSort.WINTER),
    ),
    "golden-landscape" to LookGuide(
        "片屿从晴日暖光与冷色阴影的对照提炼配方，以 Velvia 100 正片直扫模型为底。",
        "暖金高光与深青蓝阴影分开，色彩反差更明显。偏暖和偏冷的区域都需要原片提供一些色彩基础。",
        "适合夕照、秋色山景、晴日草地和阴影有冷色的街景。以人物为主时可以收低强度。",
        setOf(LookSort.WARM, LookSort.DAYLIGHT, LookSort.SUMMER, LookSort.AUTUMN),
    ),
    "blue-hour" to LookGuide(
        "片屿参考傍晚样片的冷色层次，以 Provia 100F 正片直扫模型为底。",
        "冷青蓝更明显，暗部轻带紫，绿色稍收。蓝调天空和远处亮点会形成较安静的明暗关系。",
        "适合日落后的城市、湖面和以天空为主的暮色。冷色容易上到肤色，拍人时可降低强度。",
        setOf(LookSort.COOL, LookSort.DAYLIGHT, LookSort.WINTER, LookSort.AUTUMN),
    ),
    "forest-deep" to LookGuide(
        "片屿在林地样片对照中提炼深绿方向，以 Provia 100F 正片直扫模型为底。",
        "整体彩度较低，深绿、树皮的棕色和高光分得更清楚。暗部有重量，亮处的过渡偏柔。",
        "适合森林、草地、瀑布和绿色占比高的风景。想保留鲜艳花色或清亮蓝天时建议减弱。",
        setOf(LookSort.COOL, LookSort.DAYLIGHT, LookSort.SPRING, LookSort.AUTUMN),
    ),
    "muted-teal-coast" to LookGuide(
        "片屿从阴天水景和雾岸样片提炼灰青方向，采用 Portra 400 负片与 Portra Endura 相纸模型。",
        "青色带灰，饱和度与反差都偏低，水面、天空和薄雾的过渡更柔和。",
        "适合阴天海岸、远山、水面和清静的街道。若希望保留鲜艳招牌或强日照，先把强度调低。",
        setOf(LookSort.COOL, LookSort.DAYLIGHT, LookSort.AUTUMN, LookSort.WINTER),
    ),
    "spring-cool" to LookGuide(
        "片屿以春日嫩叶和清淡自然光为配色参考，采用 Provia 100F 正片直扫模型。",
        "浅绿更清透，亮处略带凉意，反差柔和。白墙与浅色花草之间的层次比较轻。",
        "适合窗边植物、嫩叶、阴天花草和浅色日常，夏日也可以用来表达轻盈和凉爽。",
        setOf(LookSort.COOL, LookSort.DAYLIGHT, LookSort.SPRING),
    ),
    "spring-warm" to LookGuide(
        "片屿以春日花色与浅暖窗光为配色参考，采用 Portra 400 负片与 Portra Endura 相纸模型。",
        "暖色偏粉，肤色与浅色物件略亮，高光向柔和过渡。整体颜色轻，适合浅色主体。",
        "适合人像、浅色衣服、花朵和室内静物。白色或肤色本来偏黄时，先降强度。",
        setOf(LookSort.WARM, LookSort.DAYLIGHT, LookSort.SPRING),
    ),
    "summer-warm" to LookGuide(
        "片屿以夏日直射日光和草地的金黄层次为配色参考，采用 Velvia 100 正片直扫模型。",
        "金黄更鲜明，暖光的存在感增强，蓝天和亮处保留层次，饱和度轻提。",
        "适合晴天街景、午后草地、暖阳和建筑外立面。以肤色或橙红物件为主时建议稍低强度。",
        setOf(LookSort.WARM, LookSort.DAYLIGHT, LookSort.SUMMER),
    ),
    "autumn-cool" to LookGuide(
        "片屿以秋冬阴天、灰墙和薄雾为配色参考，采用 Provia 100F 正片直扫模型。",
        "冷灰和青绿更明显，黄绿色收敛，阴影稍提。中低饱和度让远景和雾气过渡更平静。",
        "适合阴天、薄雾、灰墙和想突出凉意的风景。灯下人像想保留暖意时，可以减弱强度。",
        setOf(LookSort.COOL, LookSort.DAYLIGHT, LookSort.AUTUMN, LookSort.WINTER),
    ),
    "autumn-warm" to LookGuide(
        "片屿从山间薄雾样片试色中提炼配方，以 Provia 100F 正片直扫模型为底。",
        "铜红与金棕更暖，白雾和水花提亮，岩壁与近景阴影更深。雾、岩壁与植被之间的明暗更分明。",
        "适合秋叶、暖灰岩壁、瀑布和有前后景的薄雾。暗处有主要人物时，可以先减弱强度。",
        setOf(LookSort.WARM, LookSort.DAYLIGHT, LookSort.AUTUMN),
    ),
    "cinema-tungsten-cool-bright" to LookGuide(
        "片屿用 Vision3 500T 电影负片和 Kodak 2383 印片模型试作冷亮方向，加了青蓝并轻抬阴影。",
        "青蓝更清晰，中间调更明亮，反差比深阴影的电影配方温和。肤色会受到少量冷色影响。",
        "适合夏日街景、绿荫、水边和偏白的 LED 灯下想做清凉画面的场景。肤色需要更暖时可以降低强度。",
        setOf(LookSort.COOL, LookSort.DAYLIGHT, LookSort.ARTIFICIAL, LookSort.SUMMER, LookSort.WINTER),
    ),
    "cinema-daylight-warm-bright" to LookGuide(
        "片屿用 Vision3 250D 电影负片和 Kodak 2383 印片模型试作暖亮方向，提亮中间调并加强暖橙。",
        "暖橙略亮、反差适中，白盘和灯下高光过渡柔和，暖灯的光感更容易保留。",
        "适合暖灯下的人像、餐桌、美食和室内静物。白盘或肤色已经偏黄时把强度调低。",
        setOf(LookSort.WARM, LookSort.ARTIFICIAL, LookSort.SPRING, LookSort.AUTUMN),
    ),
    "cinema-daylight-cool-shadow" to LookGuide(
        "片屿用 Vision3 250D 电影负片和 Kodak 2383 印片模型试作冷影方向，加深青蓝并压低阴影。",
        "冷色影子更深，明暗边界更清楚，适合让霓虹、门口灯光等亮点从暗背景里分离。",
        "适合夜街、霓虹、店门和有方向的混合光。人物位于较暗处时建议先低强度试用。",
        setOf(LookSort.COOL, LookSort.ARTIFICIAL, LookSort.AUTUMN, LookSort.WINTER),
    ),
    "cinema-tungsten-warm-shadow" to LookGuide(
        "片屿用 Vision3 500T 电影负片和 Kodak 2383 印片模型试作暖影方向，着重加强琥珀光并压深阴影。",
        "灯光偏琥珀，棕橙更突出，深色区域更厚。亮处和阴影之间有明显的色彩与明暗反差。",
        "适合台灯、白炽灯、街灯、舞台暖光和夜间室内。肤色已偏橙或暗部细节较少时，用较低强度。",
        setOf(LookSort.WARM, LookSort.ARTIFICIAL, LookSort.AUTUMN, LookSort.WINTER),
    ),
)
