# 王有用 MES APP

独立的 Vue 3 + Capacitor Android 现场端工程。当前版本先完成静态信息架构和交互入口，使用演示数据，不会写入现有 MES 数据库。

## 已建设页面

- 登录页
- 首页：生产执行、质量与仓储、现场管理共 18 个菜单入口
- 生产统计页
- 通知中心
- 个人中心

首页入口覆盖生产任务、扫码报工、工序流转、生产领退料、产品入库、质检、采购入库、库存、条码、装箱、设备、工装、排班和基础资料等现场使用场景。

## 本地运行

```bash
pnpm install
pnpm dev
```

浏览器打开 `http://localhost:4173`。服务端地址通过 `.env` 中的 `VITE_MES_API_BASE_URL` 配置；Android 模拟器或真机访问电脑服务端时，应填写电脑的局域网 IP，而不是 `localhost`。

## Android 构建

```bash
pnpm build
pnpm android:sync
cd android
./gradlew assembleDebug
```

生成的调试包路径为：`android/app/build/outputs/apk/debug/app-debug.apk`。

> 该工程使用 Capacitor 7，Android 构建需 Android SDK Platform 35、Build Tools 35 和 Java 21。

## 后续接口接入范围

现有后端的 `/mobile/**` 接口能够直接对接登录、个人信息、消息/待办、物料/供应商/工位/SOP、生产任务/报工/领料/流转、质检模板、库存和采购入库。设备、工装、排班、条码和装箱入口已建立，待后端补充移动端接口后接入。
