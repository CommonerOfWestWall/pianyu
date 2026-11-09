# 0.3品牌矢量

重新手工绘制的Android VectorDrawable，无边框。岛岸、日光和一片倒影表达“照片与片刻，留成一座屿”；沿用石墨#20211F、暖白#F4EBDD、黄铜#C7A269，不沿用0.2位图的白框、圆角或外轮廓。

主文件：`app/src/main/res/drawable/brand_mark.xml`。108dp透明前景，独立满铺背景，所有有效像素位于中心半径33dp的安全圆内；系统决定外部裁切。`brand_monochrome.xml`提供同形状主题单色层。0.3生产图标不使用AI位图，旧原稿仅保留作来源记录。

512px列表图标是满铺不透明正方形，不预加圆角/外阴影；另附圆形、圆角方形、squircle、水滴模拟和设备实际AdaptiveIconDrawable渲染，模拟图不冒充其他手机实测。

官方依据（2026-11-09检索）：
- https://developer.android.com/develop/ui/compose/system/icon_design_adaptive?hl=en
- https://developer.android.com/distribute/google-play/resources/icon-design-specifications
- https://codelabs.developers.google.com/design-android-launcher?hl=en

去掉内部白框是针对双重边界的视觉判断，Android并未禁止所有带边框的标志。
