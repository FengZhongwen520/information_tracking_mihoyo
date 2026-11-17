(function () {
  try {
    var stored = localStorage.getItem('theme')
    var mode = stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system'
    var dark = mode === 'dark' || (mode === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
    var theme = dark ? 'dark' : 'light'
    document.documentElement.dataset.theme = theme
    document.documentElement.style.colorScheme = theme
    var meta = document.querySelector('meta[name="theme-color"]')
    if (meta) meta.setAttribute('content', dark ? '#000000' : '#F2F2F7')
  } catch (error) {
    document.documentElement.dataset.theme = 'light'
  }
})()
