# X-Mokee-WarpShare

内置在魔趣(Mokee)Rom 中的`跃传`没有单独发布 apk ，移植了一下做了一些调整。

* Android to Mac : AirDrop 
* Android to PC : 微软官方 NearShare 实现 [https://support.microsoft.com/en-us/windows/share-things-with-nearby-devices-in-windows-10-0efbfe40-e3e2-581b-13f4-1a0e9936c2d9](https://support.microsoft.com/en-us/windows/share-things-with-nearby-devices-in-windows-10-0efbfe40-e3e2-581b-13f4-1a0e9936c2d9)
* 搜设备通过 WIFI + BLE

跃传源代码:[android_packages_apps_WarpShare]


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

### Knowning Issue

* `可被其他设备发现`的功能不可用
* 扫描到的设备可能重复出现
* 先进入 APP，再马上通过系统分享调出`WarpShare`，设备加载偏慢

### TODO

- [ ] 支持其他设备分享到本机
- [ ] 适配暗色模式
- [ ] 适配 Edge-to-Edge
- [ ] 适配 Material You
- [ ] 测试低版本 Android 系统兼容性

[xiejinpeng007]: https://github.com/xiejinpeng007/X-Mokee-WrapShare
[android_packages_apps_WarpShare]: https://github.com/MoKee/android_packages_apps_WarpShare
