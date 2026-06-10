const MIN_BROWSER_VERSIONS = {
  Firefox: 90,
  Chrome: 90,
  Edge: 90,
  Safari: 14.1
}

const SUPPORTED_BROWSER_TEXT = 'Firefox 90+、Chrome 90+、Edge 90+、Safari 14.1+'

function parseVersion(value) {
  const version = Number.parseFloat(value)
  return Number.isFinite(version) ? version : 0
}

function detectBrowser(userAgent) {
  const ua = userAgent || ''
  if (/MSIE |Trident\//.test(ua)) {
    return { name: 'Internet Explorer', version: 0 }
  }

  const edge = ua.match(/Edg\/(\d+(?:\.\d+)?)/)
  if (edge) {
    return { name: 'Edge', version: parseVersion(edge[1]) }
  }

  const firefox = ua.match(/Firefox\/(\d+(?:\.\d+)?)/)
  if (firefox) {
    return { name: 'Firefox', version: parseVersion(firefox[1]) }
  }

  const chrome = ua.match(/(?:Chrome|CriOS)\/(\d+(?:\.\d+)?)/)
  if (chrome && !/OPR\//.test(ua)) {
    return { name: 'Chrome', version: parseVersion(chrome[1]) }
  }

  const safari = ua.match(/Version\/(\d+(?:\.\d+)?).*Safari\//)
  if (safari && !/Chrome|CriOS|Chromium|Edg|OPR\//.test(ua)) {
    return { name: 'Safari', version: parseVersion(safari[1]) }
  }

  return { name: '未知浏览器', version: 0 }
}

export function getBrowserSupport(userAgent = navigator.userAgent) {
  const browser = detectBrowser(userAgent)
  const minimum = MIN_BROWSER_VERSIONS[browser.name]

  if (!minimum) {
    return {
      supported: browser.name === '未知浏览器',
      browser,
      minimum: null,
      supportedBrowserText: SUPPORTED_BROWSER_TEXT
    }
  }

  return {
    supported: browser.version >= minimum,
    browser,
    minimum,
    supportedBrowserText: SUPPORTED_BROWSER_TEXT
  }
}

export function renderUnsupportedBrowser(result) {
  const app = document.getElementById('app')
  if (!app) return

  const browserName = result.browser.name
  const browserVersion = result.browser.version ? ` ${result.browser.version}` : ''
  const currentBrowser = `${browserName}${browserVersion}`

  app.innerHTML = `
    <main class="browser-unsupported-page">
      <section class="browser-unsupported-panel">
        <p class="browser-unsupported-eyebrow">浏览器版本过低</p>
        <h1>请升级浏览器后访问</h1>
        <p class="browser-unsupported-message">
          当前浏览器为 ${currentBrowser}，系统需要使用 ${result.supportedBrowserText}。
        </p>
        <p class="browser-unsupported-note">
          当前版本无法完整支持 PDF 预览所需的现代 JavaScript 能力，升级后再刷新页面。
        </p>
      </section>
    </main>
  `
}
