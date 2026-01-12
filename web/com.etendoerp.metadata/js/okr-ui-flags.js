(function () {

  /**
   * Maximum number of retries while waiting for the target tab to be ready.
   * MAX_ATTEMPTS * RETRY_DELAY defines the total wait time.
   * (50 * 100ms = ~5 seconds)
   */
  const MAX_ATTEMPTS = 50;
  const RETRY_DELAY = 100;

  // Internal retry counter
  let attempts = 0;

  /**
   * Reads UI behavior flags from URL query parameters.
   *
   * IMPORTANT:
   * - Parameters MUST be defined before the hash (#)
   * - The hash is exclusively reserved for Etendo navigation state
   *
   * Supported flags:
   * - kiosk=true   → hides both top navigation and tab bar
   * - topnav=true  → hides top navigation
   * - tabhide=true → hides tab bar
   */
  function getFlags() {
    const search = window.location.search || '';

    return {
      hideTopNav: /topnav=true|kiosk=true/i.test(search),
      hideTabs: /tabhide=true|kiosk=true/i.test(search)
    };
  }

  /**
   * Checks whether the target tab (process or window) is fully initialized
   * and safe to apply UI changes.
   *
   * A tab is considered ready when:
   * - A tab is selected
   * - The selected tab is NOT the Workspace
   * - The tab pane has been rendered (drawn)
   *
   * This avoids breaking SmartClient layout calculations.
   */
  function isTargetTabReady() {
    const tab = OB?.MainView?.TabSet?.getSelectedTab();

    return (
      tab &&
      // Workspace is always created first and must be ignored
      tab.viewId !== '__OBMyOpenbravoImplementation__' &&
      // Ensure the tab content has completed rendering
      tab.pane?.isDrawn?.()
    );
  }

  /**
   * Polls until Etendo core objects and the target tab are ready,
   * then applies the requested UI restrictions.
   *
   * The polling stops automatically after MAX_ATTEMPTS
   * to avoid infinite loops in case of unexpected failures.
   */
  function waitAndApply() {
    attempts++;

    // Abort polling after reaching the maximum number of attempts
    if (attempts > MAX_ATTEMPTS) {
      return;
    }

    // Ensure the OB namespace is available before proceeding
    if (!window.OB) {
      return setTimeout(waitAndApply, RETRY_DELAY);
    }

    // Apply UI changes only when the target tab is fully initialized
    if (isTargetTabReady()) {
      const flags = getFlags();

      // Hide the top navigation layout if requested
      if (flags.hideTopNav && OB.TopLayout) {
        OB.TopLayout.hide();
      }

      // Hide the tab bar if requested
      if (flags.hideTabs && OB.MainView?.TabSet?.tabBar) {
        OB.MainView.TabSet.tabBar.hide();
      }
    } else {
      // Retry until the target tab becomes ready
      setTimeout(waitAndApply, RETRY_DELAY);
    }
  }

  // Script entry point
  waitAndApply();

})();
