# 动效系统

技术栈：React 18.3 + Vite 5 + TypeScript + React Router 6，样式集中在 `frontend/src/styles.css`，动效令牌与规则在 `frontend/src/motion.css`。

## 设计取向

- 方向：Swiss precise / tech。信息密度高，动效只负责确认状态、建立层级和提示内容位置。
- 原则：短、轻、可中断。不做滚动劫持、pin、视差、炫光和持续循环动画。
- 首屏不隐藏：标题、说明和第一批内容直接可见，避免为了入场动画牺牲 LCP。
- 分层入场：活动行、挑战卡、兑换码、设置块使用不同位移与时长，不做全站统一 fade-up。

## 令牌

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `--motion-dur-press` | `110ms` | 按钮、导航按压 |
| `--motion-dur-fast` | `160ms` | 颜色、边框、背景切换 |
| `--motion-dur-base` | `220ms` | Tab、提示条、面板、卡片 hover |
| `--motion-dur-enter` | `420ms` | 常规入场 |
| `--motion-dur-slow` | `560ms` | 大块内容（保留） |
| `--motion-ease-enter` | `cubic-bezier(0.22, 1, 0.36, 1)` | 入场、展开 |
| `--motion-ease-move` | `cubic-bezier(0.65, 0, 0.35, 1)` | 屏幕内移动 |
| `--motion-ease-exit` | `cubic-bezier(0.32, 0, 0.67, 0)` | 退出 |
| `--motion-ease-standard` | `cubic-bezier(0.2, 0, 0, 1)` | 通用状态变化 |
| `--motion-spring-snappy` | `linear(...)` 540ms | 开关滑块，轻微回弹，最大过冲 1.006 |

没有使用 `linear` 作为界面过渡曲线。`linear()` 只用于描述弹簧轨迹。

## 入场变体

| 变体 | 位移 / 缩放 | 时长 | 使用位置 |
| --- | --- | --- | --- |
| `row` | 文字与侧栏上移 `8px`，封面不动 | `460ms` | 活动列表行 |
| `card` | 上移 `8px` + `scale(0.994)` | `420ms` | 挑战卡、卡池卡 |
| `compact` | 上移 `5px` | `320ms` | 兑换码 |
| `fade` | 纯淡入 | `300ms` | 设置块 |

同级元素最多错位 4 个，每个 `38ms`，最大延迟 `152ms`。视口 92% 以上的内容直接以 `instant` 显示，不播放隐藏动画。

实现位置：

- `frontend/src/lib/reveal.ts`：IntersectionObserver、首屏判定、错位延迟、reduced-motion 降级。
- `frontend/src/motion.css`：令牌、变体、组件状态与 keyframes。
- 页面通过 `useReveal([...])` 注册，元素通过 `data-reveal="pending"` 与 `data-reveal-variant` 标记。

## 组件规则

- 按钮：hover 只改变背景、颜色或 1px 抬升；按压 `scale(0.97)`，`110ms`。
- 卡片：hover 以边框和阴影为主，抬升最多 1px；不旋转、不倾斜。
- Tab：选中时 `scale(0.975)` 回正，`220ms`；不做滑动高亮横条。
- 移动端筛选面板：展开时淡入并下移 4px，`220ms`。
- 登录卡：首次进入淡入并上移 10px，`480ms`。
- 提示条：从 -5px 淡入，`220ms`。
- 开关：滑块用轻微 spring，避免机械线性。
- 主题图标：切换时 6° 旋转 + 0.86 缩放回正，`220ms`。

## 滚动

`frontend/src/components/SmoothScroll.tsx` 使用 Lenis 1.3，除 reduced-motion 外都会初始化：

```ts
{ lerp: 0.1, wheelMultiplier: 1, touchMultiplier: 1, syncTouch: true, touchInertiaMultiplier: 0, smoothWheel: true, overscroll: false }
```

- 桌面滚轮有阻尼；移动端开启 `syncTouch`，拖动阶段 1:1 跟手，不额外延迟手指位置。
- Lenis 自带的触摸释放惯性不稳定（实测尾巴在 `0～42px` 间跳动），因此设 `touchInertiaMultiplier: 0`，改由本地逻辑接管松手后的滑行。
- 松手时取最后 `110ms` 的触摸样本计算速度，滑行距离为 `速度 × 240ms`，上限 `420px`，再用 `lerp: 0.13` 收敛。慢拖后停顿再松手不产生惯性；轻点屏幕会立即停住。
- `.filter-tabs` 与 `.app-nav` 标记 `data-lenis-prevent-touch`，横向滑动交还浏览器，自定义惯性也会跳过这些区域。
- 单 RAF 驱动；reduced-motion 切换时会销毁或重建实例。
- 路由切换通过 `lenis.scrollTo(0, { immediate: true })` 瞬间回顶，不播放整页滚动。
- 不拦截锚点以外的滚动手势，不做横向滚动或视差。

## 性能约束

- 只动画 `opacity`、`translate`、`scale`、`transform` 和颜色类属性。
- 入场位移使用独立的 `translate` / `scale`，卡片 hover 使用 `transform`，两者不会互相覆盖。
- 活动行只动画文字与倒计时侧栏，封面图保持静止，避免移动端滚动时反复重绘全宽大图。
- 不改变 `width`、`height`、`top`、`left`、`margin` 等布局属性。
- 离屏内容只设透明度与位移，仍占据原有布局，避免入场造成 CLS。

## Reduced motion

`prefers-reduced-motion: reduce` 下：

- 不创建 Lenis，恢复原生即时滚动。
- 所有 `[data-reveal]` 直接可见，动画为 `none`。
- 登录卡、提示条、筛选面板、Tab 选中、主题图标动画关闭。
- 卡片、开关的过渡与 hover/active 位移关闭。

## 验证记录

- `npm run build` 通过。
- Chromium 真实滚轮：250ms 时 `scrollY=958`，1.35s 后稳定在 `1200`，存在自然阻尼。
- 生产构建审计（1440 / 390，普通与 reduced-motion）：帧 P95 均为 `16.8ms`，无 stuck-invisible、无控制台错误、无横向溢出。
- 主页 / 挑战 / 兑换码 / 卡池 / 设置 / 登录逐页审计通过；数据加载造成的页脚位移最大 `0.079`，低于 `0.1`。
- 移动端筛选展开、排序切换、登录后勾选、刷新后完成状态保持、兑换码复制、主题切换均通过真实浏览器操作验证。

### CLS 修复说明

加载中的 `.empty-panel` 与活动列表 `.activity-list` 都是同层 `div`，React 会复用同一个 DOM 节点。移动端首帧会短暂保留 `48px 24px` 的旧内边距，随后收缩，产生 `0.235` 的布局位移。为各分支加上稳定 `key` 后，节点正确替换，CLS 降至 `0`（普通）与 `0.079`（reduced-motion，仅剩页脚被数据撑开）。

### 移动端抖动修复（2026-11-17）

- 根因不在 Lenis：活动行整行（含全宽封面图）参与入场动画，移动端滚动时反复重绘大图造成掉帧抖动。
- 改为封面保持静止，仅 `.row-main` 与 `.row-side` 上移 8px 淡入；封面图补 `decoding="async"`、`fetchPriority="low"`。
- 封面不再参与入场动画后，移动端重新启用 Lenis 触摸平滑；Lenis 自身释放惯性关闭，改为按最后 `110ms` 手势速度计算滑行距离。
- 移动端筛选面板展开后与首张卡片保留 16px 间距。
- 复测（390px 真实触摸事件）：中速滑动释放尾巴约 `94～100px`，快速滑动约 `128～129px`，慢拖停顿释放 `0px`；触摸中断惯性后位移 `0px`，滚到底部无过冲。
- 滚动期间帧 P95 `17ms`、长任务 `0`、方向反转 `0`，无横向溢出；reduced-motion 下不创建 Lenis，保留原生滚动。
