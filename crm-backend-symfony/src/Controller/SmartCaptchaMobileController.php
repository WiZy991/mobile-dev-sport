<?php

declare(strict_types=1);

namespace App\Controller;

use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

/**
 * HTML для Android WebView. Хост должен быть worldcashfit.ru
 * (в ЛК SmartCaptcha включена проверка домена).
 */
final class SmartCaptchaMobileController
{
    #[Route('/smartcaptcha-mobile.html', name: 'smartcaptcha_mobile', methods: ['GET'])]
    public function __invoke(): Response
    {
        $html = <<<'HTML'
<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
  <title>SmartCaptcha</title>
  <style>
    html,body{margin:0;padding:16px;background:#fff;font-family:system-ui,sans-serif;touch-action:pan-y}
    #status{color:#666;font-size:14px;margin-bottom:12px}
    #captcha-container{min-height:120px}
  </style>
  <script>
    function onSmartCaptchaReady() {
      if (!window.smartCaptcha) {
        setStatus('Не удалось загрузить капчу');
        return;
      }
      const params = getParameters();
      if (!params.sitekey) {
        setStatus('Нет sitekey');
        return;
      }
      params.webview = true;
      params.hl = params.hl || 'ru';
      setStatus('');
      const widgetId = window.smartCaptcha.render('captcha-container', params);
      window.smartCaptcha.subscribe(widgetId, 'success', handleSuccess);
      window.smartCaptcha.subscribe(widgetId, 'challenge-visible', handleChallengeVisible);
      window.smartCaptcha.subscribe(widgetId, 'challenge-hidden', handleChallengeHidden);
      if (params.invisible) window.smartCaptcha.execute(widgetId);
    }
    function setStatus(t) {
      const el = document.getElementById('status');
      if (el) el.textContent = t || '';
    }
    function handleSuccess(token) {
      if (window.NativeClient && window.NativeClient.onGetToken) {
        window.NativeClient.onGetToken(token);
      }
    }
    function handleChallengeVisible() {
      if (window.NativeClient && window.NativeClient.onChallengeVisible) {
        window.NativeClient.onChallengeVisible();
      }
    }
    function handleChallengeHidden() {
      if (window.NativeClient && window.NativeClient.onChallengeHidden) {
        window.NativeClient.onChallengeHidden();
      }
    }
    function getParameters() {
      const result = { webview: true, hl: 'ru' };
      if (!window.location.search) return result;
      new URLSearchParams(window.location.search).forEach((value, key) => {
        result[key] = value;
      });
      result.test = result.test === 'true';
      result.invisible = result.invisible === 'true';
      result.hideShield = result.hideShield === 'true';
      result.webview = true;
      return result;
    }
  </script>
  <script src="https://smartcaptcha.yandexcloud.net/captcha.js?render=onload&onload=onSmartCaptchaReady" defer></script>
</head>
<body>
  <div id="status">Загрузка…</div>
  <div id="captcha-container" class="smart-captcha"></div>
</body>
</html>
HTML;

        return new Response($html, 200, [
            'Content-Type' => 'text/html; charset=UTF-8',
            'Cache-Control' => 'no-store',
        ]);
    }
}
