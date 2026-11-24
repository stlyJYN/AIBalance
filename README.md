# AIBalance

一个极简、轻量、本地化的原生 Android 多服务商 API 余额查询工具。支持查询 DeepSeek、小米 MiMo、智谱 GLM 的账户余额，也可以自定义添加任意服务商的余额接口，并提供桌面小组件和近 15 天的消耗走势图。

> 非任何厂商官方项目，仅供个人查询与管理使用。

## 功能

- 多服务商：内置 DeepSeek、小米 MiMo、智谱 GLM，支持自定义添加服务商
- 每个服务商独立保存自己的 API Key
- 查询账户总余额，进入应用自动查询一次，点击“查询余额”立即刷新
- 今日消耗按余额变化自动累计（当日 0 点重置基线）
- 消耗走势图按天聚合，只保留最近 15 天，包含余额、今日消耗、今日 Token 三条曲线
- DeepSeek 显示当前高峰/空闲时段、UTC+8 时间与倒计时，节假日数据自动更新
- 桌面小组件：2×1、2×2、4×2 三种尺寸，点击即可打开应用并刷新
- 纯黑灰极简界面，无第三方依赖、无广告、无统计

## 支持的服务商

| 服务商 | 余额接口 | 认证方式 | 今日 Token |
| --- | --- | --- | --- |
| DeepSeek | `GET https://api.deepseek.com/user/balance` | `Authorization: Bearer <key>` | 不支持（API Key 无对应接口） |
| 小米 MiMo | `GET https://api.xiaomimimo.com/v1/platform/balance` | `api-key: <key>` | `GET /v1/platform/usage/daily` |
| 智谱 GLM | `GET https://open.bigmodel.cn/api/biz/account/query-customer-account-report` | `Authorization: Bearer <key>` | 不支持（API Key 无公开 token 接口） |
| 自定义 | 任意 URL | 任意请求头模板 | 可选用量接口 + JSON 路径 |

智谱的余额接口为平台内部接口，非公开文档，存在变动风险；如失效可在“添加自定义服务商”中自行配置。

## 安装

下载 [release/aibalance-V4.2.apk](release/aibalance-V4.2.apk) 发送到 Android 手机并安装。要求 Android 6.0（API 23）及以上，Android 15+ 已适配边到边显示。

## 使用

1. 在对应厂商开放平台创建 API Key。
2. 打开应用，选择服务商。
3. 粘贴 API Key，点击“查询余额”，或等待自动刷新。
4. 在顶部服务商条点击“＋ 添加”可添加自定义服务商；长按自定义服务商可删除。
5. 点击刷新频率文字可调整前台刷新间隔，长按可调整小组件后台刷新频率。

## 自定义服务商

添加自定义服务商时支持以下配置：

- 名称、余额接口 URL（URL 中的 `{key}` 会被替换为密钥）
- 鉴权头名称与值模板（值模板中的 `{key}` 会被替换为密钥）
- 余额 JSON 路径、币种符号
- 可选的用量接口 URL（支持 `{today}` 占位）与 Token JSON 路径

JSON 路径支持：

```text
a.b.c                        普通字段访问
a[] 或 a[0]                  数组第一个元素 / 指定下标
a[currency=CNY].b            数组中第一个匹配字段的元素
sum(a.total_tokens)          对数组内某数值字段求和
pathA|pathB                  多个候选路径，取第一个非空值
```

余额路径示例：

```text
balance_infos[currency=CNY].total_balance
balance_infos[currency=CNY].total_available
data.availableBalance|data.balance
```

## 隐私与安全

- 不内置任何 API Key
- API Key 只发送给所选服务商的余额/用量接口
- 勾选“记住密钥”后，密钥仅保存在应用私有 `SharedPreferences` 中（明文，个人自用）
- `android:allowBackup="false"`，避免密钥随系统备份导出
- 无第三方统计、广告或云服务

## 构建

项目使用原生 Java + Android SDK 命令行工具构建，不使用 Gradle。项目结构：

```text
AIBalance/
├── AndroidManifest.xml
├── build.ps1
├── README.md
├── src/com/deepseek/balance/
└── res/
```

构建命令：

```powershell
.\build.ps1 -SdkRoot <sdk路径> -JdkRoot <jdk路径>
```

默认读取上级目录 `android-build` 中的 `sdk` 与 `jdk-extract`，可通过参数覆盖。构建产物输出到 `build/aibalance-V4.2.apk`。

## 已知限制

- “今日消耗”基于余额差值推算，充值会重置当天基线
- DeepSeek、智谱通过 API Key 无法获取 token 用量，今日 Token 仅小米 MiMo（及配置了用量接口的自定义服务商）可用
- 后台小组件刷新受 Android / 厂商省电策略限制
- APK 使用调试证书签名，适合个人安装，不适合上架商店

## 免责声明

本项目与 DeepSeek、小米、智谱等公司无任何关联，仅供学习与个人使用。请妥善保管自己的 API Key。
