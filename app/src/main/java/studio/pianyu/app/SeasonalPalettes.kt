package studio.pianyu.app

internal val SeasonalPalettes = listOf(
    ReferencePalette(
        Look("spring-cool", "浅绿柔光", "fujifilm_provia_100f", "kodak_portra_endura",
            positive = true, defaultStrength = .38f, creative = true),
        "绿叶清透，亮处微冷，反差柔和。窗边植物、花草和想保留清淡光感的照片可以先试。",
        EditSettings(lookVersion = 0, exposure = .035f, contrast = -.025f, saturation = .035f,
            warmth = -.045f, tint = .012f, highlights = -.08f, shadows = .06f,
            hsl = HslSettings(green = HslChannel(.08f, .12f, .035f), cyan = HslChannel(.04f, .04f, .02f),
                blue = HslChannel(.02f, .05f, .03f)))),
    ReferencePalette(
        Look("spring-warm", "暖粉淡彩", "kodak_portra_400", "kodak_portra_endura",
            defaultStrength = .32f, creative = true),
        "肤色和浅色微暖带粉，反差柔和。想让人像、浅色衣服或静物多一点暖意时可以试。",
        EditSettings(lookVersion = 0, exposure = .04f, contrast = -.055f, saturation = .025f,
            warmth = .07f, tint = .018f, highlights = -.1f, shadows = .08f,
            hsl = HslSettings(red = HslChannel(.01f, .04f, .02f), orange = HslChannel(.015f, .07f, .045f),
                yellow = HslChannel(-.02f, .015f, .02f)))),
    ReferencePalette(
        Look("summer-warm", "金色日照", "fujifilm_velvia_100", "kodak_portra_endura",
            positive = true, defaultStrength = .4f, creative = true),
        "金黄更鲜明，蓝色和亮处仍有层次。晴天街景、午后草地和想要阳光暖意的照片可以先试。",
        EditSettings(lookVersion = 0, exposure = .02f, contrast = .045f, saturation = .045f,
            warmth = .08f, tint = -.012f, highlights = -.12f, shadows = .05f,
            hsl = HslSettings(orange = HslChannel(.01f, .06f, .025f), yellow = HslChannel(.02f, .1f, .02f),
                green = HslChannel(-.03f, .03f, -.02f)))),
    ReferencePalette(
        Look("autumn-cool", "冷灰薄雾", "fujifilm_provia_100f", "kodak_portra_endura",
            positive = true, defaultStrength = .38f, creative = true),
        "冷灰和青绿更明显，饱和度收敛。阴天、薄雾或想让画面安静一点时可以试。",
        EditSettings(lookVersion = 0, exposure = .01f, contrast = .02f, saturation = -.045f,
            warmth = -.065f, tint = -.02f, highlights = -.07f, shadows = .1f,
            hsl = HslSettings(yellow = HslChannel(.18f, -.08f, -.02f), green = HslChannel(-.1f, -.12f, -.04f),
                cyan = HslChannel(-.08f, .05f, -.02f), blue = HslChannel(-.03f, .08f, -.035f)))),
    ReferencePalette(
        Look("autumn-warm", "深秋暖雾", "fujifilm_provia_100f", "kodak_portra_endura",
            positive = true, defaultStrength = .408f, creative = true),
        "铜红和金棕更鲜明，白水和薄雾更亮，厚重的暗部更深。秋叶、暖灰岩壁或想让近景和远雾拉开时可以试。",
        // Includes the original recipe's forest-deep finish at strength 34.
        EditSettings(lookVersion = 0, exposure = .099533333f, contrast = .0668f, saturation = .0578f,
            warmth = .055f, tint = .005f, highlights = -.1286f, shadows = .0058f, fade = .052266667f,
            hsl = HslSettings(red = HslChannel(0f, .1936f, -.0086f), orange = HslChannel(-.04f, .2836f, .0114f),
                yellow = HslChannel(-.001666667f, .132f, .007333333f), green = HslChannel(-.049666667f, -.041733333f, -.0622f),
                cyan = HslChannel(0f, -.035666667f, -.022666667f), blue = HslChannel(0f, -.010333333f, -.022666667f))))
)
