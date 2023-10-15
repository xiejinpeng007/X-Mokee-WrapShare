# X-Mokee-WarpShare

内置在魔趣(Mokee)Rom 中的`跃传`没有单独发布 apk ，移植了一下做了一些调整。

----------
2023-10-13
----------
在 [xiejinpeng007] 的基础上二次开发

### What is done

- [x] 升级所有依赖库到最新
- [x] 重构代码 (partically)
- [x] 支持 Configuration Cache
- [x] 调试 AirDrop 到 Mac 通过
- [x] 页面重建不影响功能使用
- [x] 适配暗色模式

### Knowning Issue

* `可被其他设备发现`的功能不可用
* 扫描到的设备可能重复出现
* 先进入 APP，再马上通过系统分享调出`WarpShare`，设备加载偏慢

### TODO

- [ ] 支持其他设备分享到本机
- [ ] 适配 Edge-to-Edge
- [ ] 适配 Material You
- [ ] 测试低版本 Android 系统兼容性

[xiejinpeng007]: https://github.com/xiejinpeng007/X-Mokee-WrapShare
[android_packages_apps_WarpShare]: https://github.com/MoKee/android_packages_apps_WarpShare
