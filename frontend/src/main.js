import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

// Element Plus 暗色主题：基础样式 + dark CSS 变量层
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
// 品牌视觉层：#0b1020 玻璃拟态 + 琥珀→玫红能量设计
import './styles/tokens.css'

import App from './App.vue'

document.documentElement.classList.add('dark')

createApp(App)
  .use(createPinia())
  .use(ElementPlus, { locale: zhCn })
  .mount('#app')
