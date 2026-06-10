import './styles/tokens.css'
import './styles.css'
import { getBrowserSupport, renderUnsupportedBrowser } from './utils/browserSupport'

const browserSupport = getBrowserSupport()

if (!browserSupport.supported) {
  renderUnsupportedBrowser(browserSupport)
} else {
  Promise.all([
    import('vue'),
    import('./App.vue')
  ]).then(([{ createApp }, { default: App }]) => {
    createApp(App).mount('#app')
  })
}
