import { onMounted } from 'vue';

let loaded = false;
let loadingPromise: Promise<void> | null = null;

function loadScript(src: string) {
  return new Promise<void>((resolve, reject) => {
    const exist = document.querySelector(`script[data-legacy-src="${src}"]`) as HTMLScriptElement | null;
    if (exist) {
      resolve();
      return;
    }

    const script = document.createElement('script');
    script.src = src;
    script.async = false;
    script.defer = false;
    script.setAttribute('data-legacy-src', src);
    script.onload = () => resolve();
    script.onerror = () => reject(new Error(`Failed to load script: ${src}`));
    document.body.appendChild(script);
  });
}

export async function ensureLegacyScriptsLoaded() {
  if (loaded) return;
  if (loadingPromise) return loadingPromise;

  loadingPromise = (async () => {
    // 依赖顺序：jquery -> popper -> bootstrap -> 其它插件 -> script.js
    await loadScript('/assets/legacy/js/jquery.js');
    await loadScript('/assets/legacy/js/popper.min.js');
    await loadScript('/assets/legacy/js/bootstrap.min.js');

    // 常用插件
    await loadScript('/assets/legacy/js/owl.js');
    await loadScript('/assets/legacy/js/wow.js');
    await loadScript('/assets/legacy/js/scrollbar.js');
    await loadScript('/assets/legacy/js/jquery.fancybox.js');
    await loadScript('/assets/legacy/js/jquery-ui.js');
    await loadScript('/assets/legacy/js/appear.js');
    await loadScript('/assets/legacy/js/validate.js');

    // 入口
    await loadScript('/assets/legacy/js/script.js');

    loaded = true;
  })();

  return loadingPromise;
}

/**
 * 针对 SPA 场景：路由切换后 DOM 变了，但 legacy 的 script.js 默认只在首次加载时跑一次。
 * 这里提供一个“尽力而为”的二次初始化：
 * - 如果全局挂了某些初始化函数（未来我们可以在 public/assets/legacy/js/script.js 里加一个 window.__legacyInit 暴露），就调用它。
 * - 否则触发常见插件的最小初始化（尽量不破坏原效果）。
 */
export function reinitLegacyPlugins() {
  const w = window as any;

  if (typeof w.__legacyInit === 'function') {
    w.__legacyInit();
    return;
  }

  // 兜底：重建 WOW（如果存在）
  if (w.WOW) {
    try {
      w.__legacyWow = w.__legacyWow || new w.WOW({ mobile: true });
      if (typeof w.__legacyWow.init === 'function') w.__legacyWow.init();
    } catch {
      // ignore
    }
  }

  // 兜底：再跑一次 headerStyle/scroll 相关逻辑（script.js 内部是闭包，不一定能直接调用）
  // 触发一次 scroll 事件，很多逻辑挂在 scroll 监听上
  window.dispatchEvent(new Event('scroll'));
}

/**
 * 在组件 mounted 时确保脚本加载。
 */
export function useLegacyScripts(enable = true) {
  onMounted(async () => {
    if (!enable) return;
    await ensureLegacyScriptsLoaded();
    reinitLegacyPlugins();
  });
}
