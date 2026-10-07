(function () {

  /**
   * Envelope of the message sent to the new UI. It must match the constants of the new UI
   * (ALERT_COUNT_MESSAGE_TYPE / ALERT_COUNT_MESSAGE_ACTION).
   */
  const MESSAGE_TYPE = 'etendoAlertCount';
  const MESSAGE_ACTION = 'alertCountChanged';

  /**
   * Maximum number of retries while waiting for OB.AlertManager to be loaded.
   * MAX_ATTEMPTS * RETRY_DELAY defines the total wait time (50 * 100ms = ~5 seconds).
   */
  const MAX_ATTEMPTS = 50;
  const RETRY_DELAY = 100;

  // Internal retry counter
  let attempts = 0;

  /**
   * Returns the new UI window that opened this classic popup, or null when the
   * application was not opened by the new UI (no opener or not in kiosk mode).
   */
  function getTargetWindow() {
    const opener = window.opener;
    const isKiosk = /kiosk=true/i.test(window.location.search || '');

    if (!opener || opener.closed || !isKiosk) {
      return null;
    }
    return opener;
  }

  /**
   * OB.AlertManager listener: forwards the pending alerts count to the new UI.
   * It runs after every alert poll, including the immediate refresh that the
   * Alert Management view triggers after moving alerts between statuses.
   */
  function postCount(rpcResponse, data) {
    const target = getTargetWindow();
    if (!target || !data || typeof data.cnt !== 'number') {
      return;
    }

    try {
      target.postMessage({ type: MESSAGE_TYPE, action: MESSAGE_ACTION, payload: { cnt: data.cnt } }, '*');
    } catch (e) {
      // The opener may have navigated away or been closed: nothing to notify
    }
  }

  /**
   * Waits until OB.AlertManager is available and registers the listener.
   *
   * It never calls OB.AlertManager.call(): every call schedules a new polling
   * timer without cancelling the previous one.
   */
  function waitAndRegister() {
    attempts++;

    // Abort polling after reaching the maximum number of attempts
    if (attempts > MAX_ATTEMPTS) {
      return;
    }

    if (!window.OB || !OB.AlertManager) {
      setTimeout(waitAndRegister, RETRY_DELAY);
      return;
    }

    OB.AlertManager.addListener(postCount);
  }

  // Only the classic application opened from the new UI notifies it
  if (getTargetWindow()) {
    waitAndRegister();
  }

})();
