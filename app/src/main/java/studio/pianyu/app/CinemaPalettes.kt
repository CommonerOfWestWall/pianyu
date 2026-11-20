package studio.pianyu.app

internal val CinemaPalettes = listOf(
    ReferencePalette(
        Look("cinema-tungsten-cool-bright", "青蓝亮调", "kodak_vision3_500t", "kodak_2383",
            defaultStrength = .4f, creative = true),
        "青蓝更清楚，阴影略抬。夏日街景、绿荫或水边想拍出凉意时，可以先试这款。",
        EditSettings(lookVersion = 0, exposure = .055f, contrast = .025f, saturation = -.02f,
            warmth = -.035f, tint = -.02f, highlights = -.1f, shadows = .05f,
            hsl = HslSettings(cyan = HslChannel(-.05f, .08f, .02f), blue = HslChannel(-.04f, .1f, .02f),
                orange = HslChannel(.01f, -.04f, .03f)))),
    ReferencePalette(
        Look("cinema-daylight-warm-bright", "电影暖亮", "kodak_vision3_250d", "kodak_2383",
            defaultStrength = .4f, creative = true),
        "暖橙略提亮，高光柔和。室内暖灯下拍人或美食，想保留一点灯下暖意时可以先试。白盘或肤色太黄时，把强度调低。",
        EditSettings(lookVersion = 0, exposure = .07f, contrast = .02f, saturation = .005f,
            warmth = .065f, tint = .012f, highlights = -.11f, shadows = .06f,
            hsl = HslSettings(orange = HslChannel(.01f, .06f, .04f), yellow = HslChannel(.01f, .04f, .025f),
                cyan = HslChannel(-.02f, -.03f, .015f)))),
    ReferencePalette(
        Look("cinema-daylight-cool-shadow", "电影冷影", "kodak_vision3_250d", "kodak_2383",
            defaultStrength = .4f, creative = true),
        "青蓝更深，明暗反差更清晰。想保留霓虹和夜街的冷色，可以试这款；店门或人物太暗时，把强度调低。",
        EditSettings(lookVersion = 0, exposure = -.035f, contrast = .065f, saturation = -.025f,
            warmth = -.07f, tint = -.02f, highlights = -.07f, shadows = -.035f,
            hsl = HslSettings(cyan = HslChannel(-.06f, .1f, -.035f), blue = HslChannel(-.04f, .12f, -.045f),
                green = HslChannel(-.04f, -.04f, -.025f)))),
    ReferencePalette(
        Look("cinema-tungsten-warm-shadow", "电影暖影", "kodak_vision3_500t", "kodak_2383",
            defaultStrength = .4f, creative = true),
        "灯光偏琥珀，阴影更深。台灯、街灯或舞台暖光想拍得浓一点时可以试这款。",
        EditSettings(lookVersion = 0, exposure = -.035f, contrast = .065f, saturation = -.005f,
            warmth = .1f, tint = .025f, highlights = -.08f, shadows = -.03f,
            hsl = HslSettings(red = HslChannel(.005f, .04f, .01f), orange = HslChannel(.015f, .1f, .02f),
                yellow = HslChannel(.02f, .07f, -.005f), blue = HslChannel(-.02f, -.05f, -.04f))))
)
