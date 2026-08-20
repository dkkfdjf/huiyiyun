import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { installDirectives } from './directives'
import './styles/tokens.css'
import './styles/element-overrides.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
// 全局中文 locale:el-table 空态由默认 "No Data" 变「暂无数据」,分页/日期等文案一并中文化
app.use(ElementPlus, { locale: zhCn })
installDirectives(app)
app.mount('#app')
