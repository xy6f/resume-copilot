import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

// Element Plus 主题：基础样式 + dark CSS 变量层（html.dark 时由变量路由到暗色）
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
// 品牌视觉层 v3：浅色 #FCFCFD AI 产品感 + 单一品牌色 #7C5CFC（对标 Gamma/Kimi）
import './styles/tokens.css'

// 主题初始化：localStorage 记忆 > 默认浅色（AI 产品感主基调；App.vue 顶栏可切换）
if (localStorage.getItem('rc-theme') === 'dark') {
  document.documentElement.classList.add('dark')
}

createApp(App)
  .use(createPinia())
  .use(ElementPlus, { locale: zhCn })
  .mount('#app')
