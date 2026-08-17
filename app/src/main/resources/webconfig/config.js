(function () {
  'use strict';
  var token = document.querySelector('meta[name="portal-token"]').content;
  var demo = token === '%TOKEN%';
  var fields = ['ha_url', 'ha_token', 'broker_host', 'broker_port', 'username', 'password', 'device_name'];
  var steps = [
    { chapter: 'Maison', title: 'Connectez Home Assistant', description: 'Indiquez l’adresse de votre maison et son jeton d’accès.' },
    { chapter: 'Contrôle', title: 'Configurez MQTT', description: 'Ajoutez le serveur utilisé pour piloter le panneau à distance.' },
    { chapter: 'Vérification', title: 'Vérifiez la configuration', description: 'Tout est prêt. Confirmez pour appliquer les modifications.' }
  ];
  var sample = { ha_url: 'http://homeassistant.local:8123', ha_token: 'demo-token', broker_host: '192.168.1.10', broker_port: 1883, username: 'portal', password: '', device_name: 'Panneau salon' };
  var current = 0;
  var saving = false;
  var checkingAvailability = false;

  var languageSelect = document.getElementById('language-select');
  languageSelect.value = document.documentElement.lang;
  languageSelect.addEventListener('change', function () {
    var target = new URL(window.location.href);
    target.searchParams.set('lang', languageSelect.value);
    window.location.assign(target.toString());
  });

  function el(id) { return document.getElementById(id); }
  function url(path) { return path + '?t=' + encodeURIComponent(token); }
  function say(id, message, kind) { var node = el(id); node.textContent = message; node.className = 'status ' + (kind || ''); }
  function fill(data) { fields.forEach(function (field) { el(field).value = data[field] === undefined ? '' : data[field]; }); }
  function setHidden(node, hidden) { node.classList.toggle('hidden', hidden); }
  function updateMdnsWarning() {
    var value = el('ha_url').value.trim().toLowerCase();
    setHidden(el('ha_mdns_warning'), !/^https?:\/\/[^/?#]*\.local(?::\d+)?(?:[/?#]|$)/.test(value));
  }
  function updateMqttMdnsWarning() {
    var value = el('broker_host').value.trim().toLowerCase();
    setHidden(el('mqtt_mdns_warning'), !value.endsWith('.local'));
  }

  function buildProgress() {
    var host = el('progress-dots');
    steps.forEach(function (_, index) { var dot = document.createElement('span'); dot.className = 'progress-dot'; dot.dataset.index = index; host.appendChild(dot); });
  }

  function showStep(index) {
    current = index;
    document.querySelectorAll('.wizard-step').forEach(function (node) { setHidden(node, Number(node.dataset.step) !== current); });
    document.querySelectorAll('.progress-dot').forEach(function (node, dotIndex) { node.classList.toggle('active', dotIndex === current); node.classList.toggle('done', dotIndex < current); });
    el('step-chapter').textContent = steps[current].chapter;
    el('step-title').textContent = steps[current].title;
    el('step-description').textContent = steps[current].description;
    el('progress-label').textContent = steps[current].chapter + ' · étape ' + (current + 1) + ' sur ' + steps.length;
    setHidden(el('back'), current === 0);
    el('next').textContent = current === steps.length - 1 ? 'Enregistrer' : 'Continuer';
    if (current === 2) updateSummary();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function validCurrentStep() {
    if (current === 0) {
      var validUrl = /^https?:\/\/.+/.test(el('ha_url').value.trim());
      var validToken = el('ha_token').value.trim().length > 0;
      setHidden(el('ha_url_error'), validUrl); setHidden(el('ha_token_error'), validToken);
      if (!validUrl) el('ha_url').focus(); else if (!validToken) el('ha_token').focus();
      return validUrl && validToken;
    }
    if (current === 1) {
      var port = Number(el('broker_port').value);
      var validMqtt = el('broker_host').value.trim().length > 0 && port >= 1 && port <= 65535;
      setHidden(el('mqtt_error'), validMqtt); if (!validMqtt) el('broker_host').focus();
      return validMqtt;
    }
    return true;
  }

  function updateSummary() {
    el('summary-ha').textContent = el('ha_url').value.replace(/^https?:\/\//, '');
    el('summary-mqtt').textContent = el('broker_host').value + ':' + el('broker_port').value;
    el('summary-device').textContent = el('device_name').value || 'Portal';
  }

  function setHaCheck(stage, state, label) {
    var row = document.querySelector('[data-check="' + stage + '"]');
    row.className = 'connection-check ' + (state || '');
    el('ha_check_' + stage).textContent = label;
  }

  function resetHaChecks() {
    setHidden(el('ha_test_status'), false);
    ['host', 'port', 'token'].forEach(function (stage) { setHaCheck(stage, '', 'En attente'); });
    say('ha_test_error', '', '');
  }

  function runHaCheck(stage) {
    setHaCheck(stage, 'testing', 'Vérification…');
    return fetch(url('/api/test-ha'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ stage: stage, ha_url: el('ha_url').value.trim(), ha_token: el('ha_token').value.trim() })
    }).then(function (response) {
      return response.json().then(function (result) {
        if (!response.ok || !result.ok) { var failure = new Error(result.error || 'test_failed'); failure.code = result.error; throw failure; }
        setHaCheck(stage, 'ok', 'Validé');
      });
    });
  }

  function saveSection(path, body) {
    el('next').textContent = 'Enregistrement…';
    return fetch(url(path), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }).then(function (response) {
      return response.json().then(function (result) {
        if (!response.ok || !result.ok) { var failure = new Error('save_failed'); failure.code = 'save_failed'; throw failure; }
      });
    });
  }

  function haFailureMessage(code) {
    if (code === 'host_unresolved') return 'Adresse introuvable. Si elle se termine par .local, essayez l’adresse IP locale.';
    if (code === 'port_unreachable') return 'Adresse trouvée, mais le port Home Assistant est inaccessible.';
    if (code === 'token_required') return 'Le jeton d’accès est nécessaire.';
    if (code === 'token_rejected') return 'Home Assistant refuse ce jeton. Vérifiez qu’il s’agit d’un jeton longue durée valide.';
    if (code === 'api_unreachable') return 'Le serveur répond, mais l’API Home Assistant est inaccessible.';
    return 'Le test de connexion a échoué.';
  }

  function testHomeStep() {
    if (saving) return;
    if (demo) { showStep(1); return; }
    saving = true;
    resetHaChecks();
    el('next').disabled = true;
    el('next').textContent = 'Test en cours…';
    var activeStage = 'host';
    runHaCheck('host')
      .then(function () { activeStage = 'port'; return runHaCheck('port'); })
      .then(function () { activeStage = 'token'; return runHaCheck('token'); })
      .then(function () { return saveSection('/api/config/ha', { ha_url: el('ha_url').value.trim(), ha_token: el('ha_token').value.trim() }); })
      .then(function () { saving = false; el('next').disabled = false; showStep(1); })
      .catch(function (failure) {
        if (!failure.code || failure.code === 'server_unavailable') { saving = false; showServerUnavailable(); return; }
        saving = false;
        el('next').disabled = false;
        el('next').textContent = 'Réessayer';
        if (failure.code !== 'save_failed') setHaCheck(activeStage, 'err', 'Échec');
        say('ha_test_error', failure.code === 'save_failed' ? 'Les tests ont réussi, mais l’enregistrement a échoué.' : haFailureMessage(failure.code), 'err');
      });
  }

  function setMqttCheck(stage, state, label) {
    var row = document.querySelector('[data-mqtt-check="' + stage + '"]');
    row.className = 'connection-check ' + (state || '');
    el('mqtt_check_' + stage).textContent = label;
  }

  function resetMqttChecks() {
    setHidden(el('mqtt_test_status'), false);
    ['host', 'port', 'auth'].forEach(function (stage) { setMqttCheck(stage, '', 'En attente'); });
    say('mqtt_test_error', '', '');
  }

  function runMqttCheck(stage) {
    setMqttCheck(stage, 'testing', 'Vérification…');
    return fetch(url('/api/test-mqtt'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        stage: stage,
        broker_host: el('broker_host').value.trim(),
        broker_port: parseInt(el('broker_port').value, 10) || 0,
        username: el('username').value.trim(),
        password: el('password').value
      })
    }).then(function (response) {
      return response.json().then(function (result) {
        if (!response.ok || !result.ok) { var failure = new Error(result.error || 'test_failed'); failure.code = result.error; throw failure; }
        setMqttCheck(stage, 'ok', 'Validé');
      });
    });
  }

  function mqttFailureMessage(code) {
    if (code === 'mqtt_host_unresolved') return 'Serveur MQTT introuvable. Si son adresse se termine par .local, essayez son adresse IP locale.';
    if (code === 'mqtt_port_unreachable') return 'Serveur trouvé, mais le port MQTT est inaccessible.';
    if (code === 'mqtt_auth_failed') return 'La connexion MQTT a été refusée. Vérifiez l’identifiant et le mot de passe.';
    return 'Le test MQTT a échoué.';
  }

  function testMqttStep() {
    if (saving) return;
    if (demo) { showStep(2); return; }
    saving = true;
    resetMqttChecks();
    el('next').disabled = true;
    el('next').textContent = 'Test en cours…';
    var activeStage = 'host';
    runMqttCheck('host')
      .then(function () { activeStage = 'port'; return runMqttCheck('port'); })
      .then(function () { activeStage = 'auth'; return runMqttCheck('auth'); })
      .then(function () {
        return saveSection('/api/config/mqtt', {
          broker_host: el('broker_host').value.trim(),
          broker_port: parseInt(el('broker_port').value, 10) || 0,
          username: el('username').value.trim(),
          password: el('password').value,
          device_name: el('device_name').value.trim()
        });
      })
      .then(function () { saving = false; el('next').disabled = false; showStep(2); })
      .catch(function (failure) {
        if (!failure.code || failure.code === 'server_unavailable') { saving = false; showServerUnavailable(); return; }
        saving = false;
        el('next').disabled = false;
        el('next').textContent = 'Réessayer';
        if (failure.code !== 'save_failed') setMqttCheck(activeStage, 'err', 'Échec');
        say('mqtt_test_error', failure.code === 'save_failed' ? 'Les tests ont réussi, mais l’enregistrement a échoué.' : mqttFailureMessage(failure.code), 'err');
      });
  }

  function configBody() {
    var body = {}; fields.forEach(function (field) { body[field] = el(field).value; }); body.broker_port = parseInt(body.broker_port, 10) || 0; return body;
  }

  function saveAll() {
    if (saving) return;
    if (demo) { showCompletion(); return; }
    saving = true; el('next').disabled = true; el('next').textContent = 'Enregistrement…';
    fetch(url('/api/config'), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(configBody()) })
      .then(function (response) { return response.json(); })
      .then(function (result) { if (!result.ok) throw new Error(); showCompletion(); })
      .catch(function () { saving = false; showServerUnavailable(); });
  }

  function showCompletion() {
    setHidden(document.querySelector('.wizard-context'), true);
    setHidden(document.querySelector('.wizard-workspace'), true);
    setHidden(document.querySelector('.wizard-navigation'), true);
    setHidden(el('saved-view'), false);
    el('progress-label').textContent = 'Terminé';
    document.querySelectorAll('.progress-dot').forEach(function (dot) { dot.classList.add('done'); dot.classList.remove('active'); });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function showServerUnavailable() {
    setHidden(document.querySelector('.wizard-context'), true);
    setHidden(document.querySelector('.wizard-workspace'), true);
    setHidden(document.querySelector('.wizard-navigation'), true);
    setHidden(el('saved-view'), true);
    setHidden(el('server-offline-view'), false);
    el('progress-label').textContent = 'Connexion interrompue';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function ensureServerAvailable() {
    return fetch(url('/api/health'), { cache: 'no-store' }).then(function (response) {
      if (!response.ok) throw new Error('server_unavailable');
      return response.json();
    }).then(function (result) {
      if (!result.ok) throw new Error('server_unavailable');
    });
  }

  function runCurrentAction() {
    if (!validCurrentStep()) return;
    if (demo) {
      if (current === 0) showStep(1); else if (current === 1) showStep(2); else saveAll();
      return;
    }
    if (checkingAvailability || saving) return;
    checkingAvailability = true;
    el('next').disabled = true;
    var previousLabel = el('next').textContent;
    el('next').textContent = 'Vérification du panneau…';
    ensureServerAvailable().then(function () {
      checkingAvailability = false;
      el('next').disabled = false;
      el('next').textContent = previousLabel;
      if (current === 0) testHomeStep();
      else if (current === 1) testMqttStep();
      else saveAll();
    }).catch(function () {
      checkingAvailability = false;
      showServerUnavailable();
    });
  }

  el('next').addEventListener('click', runCurrentAction);
  el('back').addEventListener('click', function () { if (current > 0 && !saving) showStep(current - 1); });
  el('toggle-ha-token').addEventListener('click', function () { var input = el('ha_token'); var visible = input.type === 'text'; input.type = visible ? 'password' : 'text'; this.textContent = visible ? 'Afficher le jeton' : 'Masquer le jeton'; });
  el('ha_url').addEventListener('input', updateMdnsWarning);
  el('broker_host').addEventListener('input', updateMqttMdnsWarning);
  el('retry-server').addEventListener('click', function () {
    ensureServerAvailable().then(function () { window.location.reload(); }).catch(showServerUnavailable);
  });

  buildProgress(); showStep(0);
  if (demo) { fill(sample); updateMdnsWarning(); updateMqttMdnsWarning(); } else fetch(url('/api/config')).then(function (response) { if (!response.ok) throw new Error(); return response.json(); }).then(function (data) { fill(data); updateMdnsWarning(); updateMqttMdnsWarning(); }).catch(showServerUnavailable);
})();
