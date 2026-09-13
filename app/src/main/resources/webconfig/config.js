(function () {
  'use strict';

  var token = document.querySelector('meta[name="portal-token"]').content;
  var demo = token === '%TOKEN%';
  var sessionId = getSessionId();
  var snapshot = null;
  var config = {};
  var launcher = { apps: [], icon_packs: [], capabilities: {}, device: {} };
  var current = 'system';
  var busy = false;
  var readOnly = false;
  var previewDirty = false;
  var previewTimer = 0;
  var mutationQueue = Promise.resolve();
  var activePreviewId = '';
  var activePreviewSequence = null;
  var lastResponseAt = 0;
  var pollTimer = 0;
  var capabilityTimer = 0;
  var liveScreenTimer = 0;
  var recoveryPromise = null;
  var sse = null;
  var sseErrors = 0;
  var providerState = { ha: false, mqtt: false, gemini: false };
  var providerDraftDirty = false;
  var haCatalog = { integrations: [], entities: [] };
  var homeDraft = null;
  var homeDraftDirty = false;
  var el = function (id) { return document.getElementById(id); };
  var all = function (selector) { return Array.prototype.slice.call(document.querySelectorAll(selector)); };
  var hide = function (node, hidden) { if (node) node.classList.toggle('hidden', hidden); };
  var locale = /^(fr|en)(-|$)/i.test(document.documentElement.lang) ? document.documentElement.lang : 'fr';
  var apiUrl = function (path) { return path + (path.indexOf('?') >= 0 ? '&' : '?') + 't=' + encodeURIComponent(token); };

  var tapSelect = el('tap-app');
  var companionLabel = document.createElement('label');
  companionLabel.innerHTML = '<span>Home Assistant Companion</span><select id="ha-companion-app"><option value="">Aucune</option></select>';
  tapSelect.closest('label').parentNode.insertBefore(companionLabel, tapSelect.closest('label'));

  var errors = {
    host_unresolved: 'Adresse introuvable sur ce réseau.',
    port_unreachable: 'Port Home Assistant inaccessible.',
    token_rejected: 'Jeton refusé par Home Assistant.',
    api_unreachable: 'API Home Assistant inaccessible.',
    mqtt_host_unresolved: 'Broker introuvable sur ce réseau.',
    mqtt_port_unreachable: 'Port MQTT inaccessible.',
    mqtt_auth_failed: 'Identifiants refusés par le broker.',
    voice_key_rejected: 'Clé refusée par Google.',
    voice_unreachable: 'Google est inaccessible depuis le panneau.',
    voice_model_unknown: 'Modèle Gemini introuvable.',
    voice_model_not_live: 'Ce modèle ne prend pas en charge Gemini Live.',
    defaults_warning_required: 'Confirmez l’utilisation des valeurs par défaut.',
    confirmation_required: 'La confirmation est requise.',
    invalid_session: 'La session navigateur est invalide. Rechargez la page.',
    invalid_expected_revision: 'La révision reçue est invalide. Rechargez la page.',
    invalid_ha_url: 'Adresse Home Assistant invalide. Utilisez http:// ou https:// puis réessayez.',
    invalid_home_assistant_url: 'Adresse Home Assistant invalide. Utilisez http:// ou https:// puis réessayez.',
    home_assistant_token_required: 'Jeton Home Assistant requis. Saisissez un jeton longue durée ou ignorez ce provider.',
    invalid_broker_host: 'Hôte MQTT requis. Saisissez une adresse IP ou un nom DNS.',
    invalid_mqtt_host: 'Hôte MQTT requis. Saisissez une adresse IP ou un nom DNS.',
    invalid_mqtt_port: 'Port MQTT invalide. Saisissez un entier entre 1 et 65535.',
    mqtt_username_required: 'Utilisateur MQTT requis quand un mot de passe est fourni.',
    gemini_key_required: 'Clé Google AI Studio requise. Saisissez une clé ou ignorez Gemini.',
    voice_key_required: 'Clé Google AI Studio requise. Saisissez une clé ou ignorez Gemini.',
    editor_already_active: 'Un autre navigateur modifie cet appareil. Rechargez ou reprenez la main.',
    revision_conflict: 'La configuration a changé. Rechargez les valeurs avant de continuer.',
    stale_preview: 'Cet aperçu a été remplacé. Rechargez les valeurs du panneau.',
    invalid_preview_sequence: 'Cet aperçu n’est plus la version affichée sur le panneau. Rechargez les valeurs.',
    device_preview_not_applied: 'Le panneau n’a pas confirmé l’affichage. Les réglages n’ont pas été enregistrés.',
    device_not_applied: 'Le réglage est enregistré, mais le panneau ne l’a pas encore affiché. Vérifiez sa connexion.'
    ,invalid_image_type: 'Format refusé. Utilisez JPEG, PNG ou WebP.'
    ,invalid_image: 'Image illisible. Choisissez un autre fichier.'
    ,image_too_large: 'Image trop volumineuse. Taille maximale : 8 Mo.'
    ,custom_background_required: 'Ajoutez une image avant de choisir cette source.'
    ,invalid_immich_url: 'Adresse Immich invalide. HTTPS est requis sauf autorisation explicite.'
    ,immich_key_required: 'Clé Immich requise.'
    ,immich_albums_failed: 'Albums Immich indisponibles.'
    ,protected_app: 'Cette application est protégée et ne peut pas être masquée.'
    ,invalid_clock: 'Réglage d’horloge invalide.'
    ,invalid_behavior: 'Réglage de comportement invalide.'
    ,system_action_unavailable: 'Cette action système est indisponible sur le panneau.'
  };

  function getSessionId() {
    var stored = window.sessionStorage.getItem('portal-onboarding-session');
    if (stored && /^[A-Za-z0-9_-]{8,128}$/.test(stored)) return stored;
    var generated = window.crypto && window.crypto.randomUUID
      ? window.crypto.randomUUID()
      : 'web-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2);
    window.sessionStorage.setItem('portal-onboarding-session', generated);
    return generated;
  }

  function request(path, options) {
    if (demo) return Promise.resolve({ ok: true, revision: snapshot ? snapshot.revision : 0 });
    var started = performance.now();
    return fetch(apiUrl(path), options || {}).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (body) {
        markConnected(Math.round(performance.now() - started));
        if (!response.ok || body.ok === false) {
          var error = new Error(body.error || 'server_unavailable');
          error.code = body.error || 'server_unavailable';
          error.status = response.status;
          error.body = body;
          throw error;
        }
        return body;
      });
    }).catch(function (error) {
      if (!error.status) markOffline();
      throw error;
    });
  }

  function post(path, body, takeOver) {
    var run = function () {
      if (readOnly && !takeOver) return Promise.reject({ code: 'read_only' });
      var payload = Object.assign({}, body, {
        session_id: sessionId,
        expected_revision: snapshot ? snapshot.revision : -1
      });
      if (takeOver) payload.take_over = true;
      return request(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      }).then(function (data) {
        if (typeof data.revision === 'number') applySnapshot(data, false);
        return data;
      }).catch(function (error) {
        if (error.status === 409) showConflict(error);
        else if (error.code !== 'read_only') showRequestError(error.code);
        throw error;
      });
    };
    var queued = mutationQueue.catch(function () {}).then(run);
    mutationQueue = queued.catch(function () {});
    return queued;
  }

  function markConnected(latency) {
    lastResponseAt = Date.now();
    el('connection-dot').className = 'status-dot connected';
    el('connection-state').textContent = readOnly ? 'Lecture seule' : 'Connecté';
    var measured = typeof latency === 'number' && latency > 0;
    el('latency').textContent = measured ? latency + ' ms' : '— ms';
    el('device-state').textContent = measured ? 'Connecté · ' + latency + ' ms' : 'Connecté';
    hide(el('offline-state'), true);
  }

  function markOffline() {
    el('connection-dot').className = 'status-dot offline';
    el('connection-state').textContent = 'Hors ligne';
    el('latency').textContent = '— ms';
    el('device-state').textContent = 'Hors ligne';
    var detail = lastResponseAt ? 'Dernière réponse il y a ' + Math.max(1, Math.round((Date.now() - lastResponseAt) / 1000)) + ' s.' : 'Aucune réponse du panneau.';
    el('offline-detail').textContent = detail;
    hide(el('offline-state'), false);
    setReadOnly(true, false);
  }

  function showRequestError(code) {
    el('request-error-detail').textContent = errors[code] || 'Le panneau a refusé la commande. Rechargez l’état puis réessayez.';
    hide(el('request-error'), false);
  }

  function clearFieldError(fieldId) {
    var field = el(fieldId);
    var error = el(fieldId + '-error');
    if (!field) return;
    field.removeAttribute('aria-invalid');
    if (error) error.remove();
    var describedBy = (field.getAttribute('aria-describedby') || '').split(/\s+/).filter(function (id) { return id && id !== fieldId + '-error'; });
    if (describedBy.length) field.setAttribute('aria-describedby', describedBy.join(' '));
    else field.removeAttribute('aria-describedby');
  }

  function showFieldError(fieldId, code) {
    var field = el(fieldId);
    if (!field) return showRequestError(code);
    clearFieldError(fieldId);
    var message = errors[code] || 'Valeur invalide.';
    var error = document.createElement('p');
    error.id = fieldId + '-error';
    error.className = 'field-error';
    error.textContent = message;
    field.closest('label').insertAdjacentElement('afterend', error);
    field.setAttribute('aria-invalid', 'true');
    var describedBy = (field.getAttribute('aria-describedby') || '').trim();
    field.setAttribute('aria-describedby', (describedBy + ' ' + error.id).trim());
    showRequestError(code);
    field.focus();
  }

  function showConflict(error) {
    if (error.body && typeof error.body.revision === 'number') applySnapshot(error.body, false);
    var revisionConflict = error.code === 'revision_conflict';
    el('conflict-title').textContent = revisionConflict ? 'Configuration modifiée' : 'Session en lecture seule';
    el('conflict-detail').textContent = revisionConflict ? 'Le panneau ou un autre navigateur a enregistré une version plus récente.' : 'Un autre navigateur modifie cet appareil.';
    hide(el('conflict-state'), false);
    hide(el('request-error'), true);
    setReadOnly(true, true);
  }

  function setReadOnly(value, conflict) {
    readOnly = value;
    all('#config-form input, #config-form select, #config-form button, #step-navigation button').forEach(function (node) { node.disabled = value || node.hasAttribute('data-fixed-disabled'); });
    el('reload-snapshot').disabled = false;
    el('take-over').disabled = value && !conflict;
    el('reset-onboarding').disabled = value;
    if (!conflict && value) el('connection-state').textContent = 'Hors ligne';
    if (!value && lastResponseAt) el('connection-state').textContent = 'Connecté';
  }

  function syncEditorState(data) {
    var locked = !!data.editor_active && !data.editor_owned;
    if (locked) {
      el('conflict-title').textContent = 'Session en lecture seule';
      el('conflict-detail').textContent = 'Un autre navigateur modifie cet appareil.';
    }
    hide(el('conflict-state'), !locked);
    setReadOnly(locked, locked);
  }

  function activeSteps() {
    var result = ['system', 'display', 'background', 'clock', 'apps', 'behavior', 'providers'];
    if (providerState.ha) result.push('ha');
    if (providerState.mqtt) result.push('mqtt');
    if (providerState.gemini) result.push('gemini');
    result.push('review');
    return result;
  }

  function pageForStep(step) {
    if (step === 'SYSTEM_SETUP') return 'system';
    if (step === 'GRID') return 'display';
    if (step === 'BACKGROUND') return 'background';
    if (step === 'GEMINI') return providerState.gemini ? 'gemini' : 'providers';
    if (['HOME_ASSISTANT_INTRO', 'HOME_ASSISTANT_CREDENTIALS', 'HOME_ASSISTANT_TEST', 'PILLS_INTRO'].indexOf(step) >= 0) return providerState.ha ? 'ha' : 'providers';
    if (['REMOTE_CONTROL', 'MQTT_CONFIGURATION', 'MQTT_TEST'].indexOf(step) >= 0) return providerState.mqtt ? 'mqtt' : 'providers';
    if (['HIDDEN_APPS', 'TAP_APP'].indexOf(step) >= 0) return 'apps';
    if (step === 'GESTURES') return 'behavior';
    if (step === 'COMPLETE') return 'review';
    return 'system';
  }

  function coordinatorStep(page) {
    return { system: 'SYSTEM_SETUP', display: 'GRID', background: 'BACKGROUND', clock: 'BACKGROUND', apps: 'HIDDEN_APPS', behavior: 'GESTURES', providers: 'HOME_ASSISTANT_INTRO', ha: 'HOME_ASSISTANT_CREDENTIALS', mqtt: 'MQTT_CONFIGURATION', gemini: 'GEMINI', review: 'COMPLETE' }[page];
  }

  function showPage(page, persist) {
    var steps = activeSteps();
    if (steps.indexOf(page) < 0) page = steps[Math.min(steps.indexOf(current) + 1, steps.length - 1)] || 'review';
    current = page;
    all('[data-page]').forEach(function (node) { hide(node, node.dataset.page !== page); });
    all('[data-target]').forEach(function (node) {
      var enabled = steps.indexOf(node.dataset.target) >= 0;
      hide(node, !enabled);
      node.setAttribute('aria-current', node.dataset.target === page ? 'step' : 'false');
    });
    var index = steps.indexOf(page);
    all('.step-counter').forEach(function (node) { node.textContent = (index + 1) + ' / ' + steps.length; });
    el('mobile-progress').textContent = (index + 1) + ' / ' + steps.length;
    hide(el('back'), index === 0);
    el('next').textContent = page === 'review' ? 'Activer Portal' : 'Continuer';
    hide(el('revert'), !previewDirty || ['display', 'background', 'clock'].indexOf(page) < 0);
    if (page === 'system') loadLauncherState();
    if (page === 'apps') renderApps();
    if (page === 'review') updateReview();
    if (persist && snapshot && !readOnly) post('/api/onboarding/command', { command: 'navigate', step: coordinatorStep(page) }).catch(function () {});
    var heading = document.querySelector('[data-page="' + page + '"] h1');
    if (heading) { heading.setAttribute('tabindex', '-1'); heading.focus(); }
  }

  function applySnapshot(data, initial) {
    var previousRevision = snapshot && snapshot.revision;
    snapshot = Object.assign({}, snapshot || {}, data);
    el('revision-value').textContent = String(snapshot.revision);
    if (!providerDraftDirty) {
      providerState.ha = !!snapshot.ha_selected;
      providerState.mqtt = !!snapshot.mqtt_selected;
      providerState.gemini = !!snapshot.gemini_selected;
      el('provider-ha').checked = providerState.ha;
      el('provider-mqtt').checked = providerState.mqtt;
      el('provider-gemini').checked = providerState.gemini;
    }
    hide(el('no-providers-state'), providerState.ha || providerState.mqtt || providerState.gemini);
    hide(el('ha-configured'), !snapshot.ha_configured);
    hide(el('mqtt-configured'), !snapshot.mqtt_configured);
    hide(el('gemini-configured'), !snapshot.gemini_configured);
    el('gemini-enabled').checked = !!snapshot.gemini_enabled;
    el('gemini-prompt').value = snapshot.gemini_prompt || '';
    el('gemini-barge-in').checked = !!snapshot.gemini_barge_in;
    el('gemini-threshold').value = snapshot.gemini_threshold || 50;
    el('gemini-idle').value = snapshot.gemini_idle_seconds || 20;
    el('gemini-daily-limit').value = snapshot.gemini_daily_limit == null ? 200 : snapshot.gemini_daily_limit;
    el('gemini-calibration-state').textContent = snapshot.gemini_calibrated ? 'Effectuée' : 'Non effectuée';
    if (snapshot.home) {
      el('home-enabled').checked = snapshot.home.home_page_enabled !== false;
      el('home-grouping').value = snapshot.home.grouping_mode || 'type';
      if (!homeDraftDirty) homeDraft = JSON.parse(JSON.stringify(snapshot.home));
      renderHomeStructure();
    }
    if (snapshot.cameras) el('camera-mode').value = snapshot.cameras.default_mode || 'main';
    if (snapshot.gemini_wake_words) {
      el('gemini-wake-word').innerHTML = '';
      snapshot.gemini_wake_words.forEach(function (value) { var option = document.createElement('option'); option.value = value; option.textContent = value.split('/').pop().replace(/_v[\d.]+\.onnx$/, '').replace(/_/g, ' '); el('gemini-wake-word').appendChild(option); });
      el('gemini-wake-word').value = snapshot.gemini_wake_word || '';
    }
    if (snapshot.immich) {
      el('immich-url').value = snapshot.immich.url || '';
      el('immich-insecure').checked = !!snapshot.immich.allow_insecure;
      el('immich-shuffle').checked = !!snapshot.immich.shuffle;
      el('immich-refresh').value = snapshot.immich.refresh_minutes || 60;
      el('immich-cadence').value = snapshot.immich.cadence_seconds || 30;
      el('immich-key').placeholder = snapshot.immich.key_configured ? 'Configurée' : '';
    }
    if (snapshot.clock) fillClock(snapshot.clock);
    if (snapshot.behavior) fillBehavior(snapshot.behavior);
    el('notification-dots').checked = !!snapshot.notification_dots;
    hide(el('defaults-warning'), !(snapshot.warnings || []).includes('DEFAULTS_WILL_BE_USED'));
    if (previewDirty) {
      if (snapshot.preview && snapshot.preview.id === activePreviewId) {
        activePreviewSequence = snapshot.preview.sequence;
      }
    } else if (snapshot.preview) {
      activePreviewId = snapshot.preview.id || '';
      activePreviewSequence = snapshot.preview.sequence;
      el('grid-scale').value = snapshot.preview.grid_scale;
      el('background-opacity').value = snapshot.preview.background_opacity;
      selectBackground(snapshot.preview.background_mode);
    } else if (initial || previousRevision !== snapshot.revision) {
      el('grid-scale').value = snapshot.grid_scale;
      el('background-opacity').value = snapshot.background_opacity;
      selectBackground(snapshot.background_mode);
      previewDirty = false;
      activePreviewId = '';
      activePreviewSequence = null;
    }
    renderPreview();
    toggleBackgroundFields();
    if (snapshot.completed) {
      showComplete();
    } else {
      // A non-destructive reset keeps this tab and its session alive. Restore the wizard as soon
      // as the reset snapshot arrives instead of leaving the previous completion panel on top of
      // the active step.
      hide(el('saved-state'), true);
      hide(el('config-form'), false);
      if (initial) showPage(pageForStep(snapshot.step), false);
    }
  }

  function fillConfig(data) {
    config = data || {};
    el('ha-url').value = config.ha_url || '';
    el('broker-host').value = config.broker_host || '';
    el('broker-port').value = config.broker_port || 1883;
    el('mqtt-username').value = config.username || '';
    el('device-name').value = config.device_name || '';
    setSelectValue(el('gemini-model'), config.voice_gemini_model || '');
    el('gemini-voice').value = config.voice_gemini_voice || '';
    el('gemini-barge-in').checked = !!config.voice_barge_in;
    el('gemini-prompt').value = config.voice_gemini_prompt || '';
  }

  function setSelectValue(select, value) {
    if (value && !Array.prototype.some.call(select.options, function (option) { return option.value === value; })) {
      var option = document.createElement('option'); option.value = value; option.textContent = value; select.appendChild(option);
    }
    select.value = value;
  }

  function fillClock(value) {
    el('clock-font').value = value.font || 'space_grotesk';
    el('clock-weight').value = value.weight || 900;
    el('clock-size').value = value.size || 138;
    el('clock-letter-spacing').value = value.letter_spacing || 0;
    el('clock-tint').value = value.tint || 'white';
    el('clock-24h').checked = value.format_24h !== false;
    el('clock-date-format').value = value.date_format || 'long';
    el('clock-element-spacing').value = value.element_spacing || 1;
  }

  function fillBehavior(value) {
    el('keep-screen-on').checked = !!value.keep_screen_on;
    el('screen-timeout-enabled').checked = !!value.screen_timeout_enabled;
    el('screen-timeout-minutes').value = value.screen_timeout_minutes || 10;
    el('auto-return-enabled').checked = !!value.auto_return_enabled;
    el('auto-return-delay').value = value.auto_return_delay_seconds || 10;
    el('gestures-seen').checked = !!snapshot.gestures_seen;
  }

  function clockBody() {
    return {
      font: el('clock-font').value,
      weight: Number(el('clock-weight').value),
      size: Number(el('clock-size').value),
      letter_spacing: Number(el('clock-letter-spacing').value),
      tint: el('clock-tint').value,
      format_24h: el('clock-24h').checked,
      date_format: el('clock-date-format').value,
      element_spacing: Number(el('clock-element-spacing').value)
    };
  }

  function toggleBackgroundFields() {
    var mode = selectedBackground();
    hide(el('custom-background-fields'), mode !== 'custom');
    hide(el('immich-background-fields'), mode !== 'immich');
  }

  function loadLauncherState() {
    hide(el('capability-error'), true);
    return request('/api/launcher').then(function (data) {
      launcher = data;
      hide(el('capability-loading'), true);
      hide(el('capability-list'), false);
      var labels = { granted: 'Accordé', missing: 'À autoriser', unavailable: 'Indisponible' };
      ['default_launcher', 'screen_control', 'brightness', 'notifications'].forEach(function (name) {
        var status = (data.capabilities || {})[name] || 'unavailable';
        var node = el('cap-' + name.replace(/_/g, '-'));
        node.textContent = labels[status] || status;
        node.dataset.status = status;
        var button = document.querySelector('[data-system-action="' + name + '"]');
        if (button) { button.disabled = readOnly || status === 'granted' || status === 'unavailable'; button.textContent = status === 'granted' ? 'Accordé' : 'Ouvrir'; }
      });
      var d = data.device || {};
      el('device-metrics').textContent = d.width_px && d.height_px ? d.width_px + ' × ' + d.height_px + ' · ' + d.density_dpi + ' dpi' : '—';
      fillLauncherCatalog();
      renderApps();
      renderPreview();
      return data;
    }).catch(function () { hide(el('capability-loading'), true); hide(el('capability-list'), true); hide(el('capability-error'), false); });
  }

  function fillLauncherCatalog() {
    var packSelect = el('icon-pack');
    var selectedPack = snapshot.icon_pack || '';
    packSelect.innerHTML = '';
    (launcher.icon_packs || []).forEach(function (pack) {
      var option = document.createElement('option'); option.value = pack.package; option.textContent = pack.label; packSelect.appendChild(option);
    });
    packSelect.value = selectedPack;
    var tap = el('tap-app');
    var companion = el('ha-companion-app');
    tap.innerHTML = '<option value="">Aucune</option>';
    companion.innerHTML = '<option value="">Aucune</option>';
    (launcher.apps || []).forEach(function (app) {
      var option = document.createElement('option'); option.value = app.package; option.textContent = app.label; tap.appendChild(option);
      companion.appendChild(option.cloneNode(true));
    });
    tap.value = snapshot.tap_app_package || '';
    companion.value = snapshot.home_assistant_package || '';
  }

  function renderApps() {
    var apps = launcher.apps || [];
    hide(el('apps-loading'), apps.length > 0);
    hide(el('apps-empty'), apps.length !== 0);
    var query = el('apps-search').value.trim().toLowerCase();
    var filter = el('apps-filter').value;
    var filtered = apps.filter(function (app) {
      var match = !query || app.label.toLowerCase().indexOf(query) >= 0 || app.package.toLowerCase().indexOf(query) >= 0;
      var visible = filter === 'all' || (filter === 'visible' && !app.hidden) || (filter === 'hidden' && app.hidden) || (filter === 'system' && app.system);
      return match && visible;
    });
    hide(el('apps-filter-empty'), apps.length === 0 || filtered.length > 0);
    hide(el('apps-table-wrap'), filtered.length === 0);
    el('apps-count').textContent = filtered.length + ' / ' + apps.length;
    var body = el('apps-table'); body.innerHTML = '';
    filtered.forEach(function (app) {
      var row = document.createElement('tr');
      var name = document.createElement('td'); name.textContent = app.label;
      var pkg = document.createElement('td'); pkg.className = 'mono'; pkg.textContent = app.package;
      var type = document.createElement('td'); type.textContent = app.protected ? 'Protégée' : app.system ? 'Système' : 'Installée';
      var visible = document.createElement('td'); var check = document.createElement('input'); check.type = 'checkbox'; check.checked = !app.hidden; check.disabled = readOnly || app.protected; check.setAttribute('aria-label', 'Afficher ' + app.label); check.addEventListener('change', function () { app.hidden = !check.checked; }); visible.appendChild(check);
      var order = document.createElement('td'); order.className = 'order-actions';
      ['Monter', 'Descendre'].forEach(function (label, direction) { var button = document.createElement('button'); button.type = 'button'; button.className = 'icon-button'; button.title = label; button.setAttribute('aria-label', label + ' ' + app.label); button.textContent = direction ? '↓' : '↑'; button.addEventListener('click', function () { moveApp(app.package, direction ? 1 : -1); }); order.appendChild(button); });
      [name, pkg, type, visible, order].forEach(function (cell) { row.appendChild(cell); }); body.appendChild(row);
    });
  }

  function moveApp(packageName, delta) {
    var apps = launcher.apps || []; var index = apps.findIndex(function (app) { return app.package === packageName; });
    var target = Math.max(0, Math.min(apps.length - 1, index + delta)); if (index < 0 || target === index) return;
    var moved = apps.splice(index, 1)[0]; apps.splice(target, 0, moved); renderApps(); renderPreview();
  }

  function selectedBackground() {
    var selected = document.querySelector('input[name="background-mode"]:checked');
    return selected ? selected.value : 'neutral';
  }

  function selectBackground(mode) {
    var target = document.querySelector('input[name="background-mode"][value="' + mode + '"]');
    (target || document.querySelector('input[name="background-mode"][value="neutral"]')).checked = true;
  }

  function renderPreview() {
    var scale = Number(el('grid-scale').value);
    var opacity = Number(el('background-opacity').value);
    var columns = Math.max(4, Math.min(8, Math.round(6 / scale)));
    var device = launcher.device || {};
    var aspect = device.width_px && device.height_px ? device.width_px / device.height_px : 1.6;
    var rows = aspect > 1.45 ? 4 : 5;
    var count = columns * rows;
    el('grid-value').textContent = scale.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    el('opacity-value').textContent = Math.round(opacity * 100) + ' %';
    el('columns-value').textContent = columns;
    el('rows-value').textContent = rows;
    el('apps-value').textContent = count;
    el('device-preview').dataset.background = selectedBackground();
    var image = el('preview-background-image');
    if (selectedBackground() === 'custom' && snapshot && snapshot.custom_background_configured) {
      image.src = apiUrl('/api/background/custom?v=' + snapshot.revision); hide(image, false); hide(el('preview-unavailable'), true);
    } else {
      hide(image, true); hide(el('preview-unavailable'), ['system', 'immich'].indexOf(selectedBackground()) < 0);
    }
    el('preview-overlay').style.opacity = String(opacity);
    el('preview-grid').style.gridTemplateColumns = 'repeat(' + columns + ', 1fr)';
    var visibleApps = (launcher.apps || []).filter(function (app) { return !app.hidden; }).slice(0, count);
    el('preview-grid').innerHTML = new Array(Math.max(count - visibleApps.length, 0) + 1).join('<span class="preview-app"></span>');
    visibleApps.forEach(function (app) { var icon = document.createElement('span'); icon.className = 'preview-app'; icon.title = app.label; el('preview-grid').appendChild(icon); });
    el('preview-grid').style.setProperty('--icon-size', Math.round(23 * scale) + 'px');
    all('[data-scale]').forEach(function (node) {
      var selected = Math.abs(Number(node.dataset.scale) - scale) < .015;
      node.classList.toggle('selected', selected);
      node.setAttribute('aria-pressed', selected ? 'true' : 'false');
    });
    var clock = clockBody();
    var colors = { white: '#fff', amber: '#ffb340', mint: '#30d158', blue: '#0a84ff', pink: '#ff7eb3', violet: '#b388ff' };
    var fontNames = { space_grotesk: 'Inter, sans-serif', inter: 'Inter, sans-serif', jetbrains_mono: 'ui-monospace, monospace', oswald: 'Arial Narrow, sans-serif', playfair: 'Georgia, serif', montserrat: 'Inter, sans-serif', orbitron: 'ui-monospace, monospace', teko: 'Arial Narrow, sans-serif', roboto_slab: 'Georgia, serif', exo2: 'Inter, sans-serif' };
    el('preview-clock').style.fontFamily = fontNames[clock.font] || fontNames.space_grotesk;
    el('preview-clock').style.fontWeight = clock.weight;
    el('preview-clock').style.fontSize = Math.round(clock.size / 7) + 'px';
    el('preview-clock').style.letterSpacing = clock.letter_spacing / 4 + 'px';
    el('preview-clock').style.color = colors[clock.tint] || '#fff';
    el('preview-date').style.color = colors[clock.tint] || '#fff';
    el('preview-date').style.top = (20 + (clock.element_spacing - 1) * 2) + '%';
    el('preview-clock').textContent = clock.format_24h ? '08:42' : '8:42 AM';
    el('simulation-state').textContent = previewDirty ? 'Non validée' : 'Synchronisée';
    hide(el('revert'), !previewDirty || ['display', 'background', 'clock'].indexOf(current) < 0);
  }

  function refreshLiveScreen() {
    if (demo || current !== 'display') return;
    var image = el('live-device-screen');
    var unavailable = el('preview-unavailable');
    var next = apiUrl('/api/onboarding/screen?v=' + Date.now());
    image.onload = function () {
      hide(image, false);
      hide(unavailable, true);
    };
    image.onerror = function () {
      hide(image, true);
      hide(unavailable, false);
    };
    image.src = next;
  }

  function launcherBody(command) {
    return { command: command, preview_id: activePreviewId, preview_sequence: activePreviewSequence, grid_scale: Number(el('grid-scale').value), background_mode: selectedBackground(), background_opacity: Number(el('background-opacity').value), clock: clockBody() };
  }

  function newPreviewId() {
    return window.crypto && window.crypto.randomUUID
      ? window.crypto.randomUUID()
      : 'preview-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2);
  }

  function schedulePreview() {
    previewDirty = true;
    if (!activePreviewId) activePreviewId = newPreviewId();
    renderPreview();
    el('preview-status').className = 'ack pending';
    el('preview-status').textContent = 'Simulation';
    window.clearTimeout(previewTimer);
    previewTimer = window.setTimeout(function () {
      post('/api/onboarding/command', launcherBody('preview_launcher')).then(function (data) {
          activePreviewSequence = data.preview && data.preview.sequence;
          var acknowledged = data.device_ack === true;
          el('preview-status').className = 'ack ' + (acknowledged ? 'applied' : 'pending');
          el('preview-status').textContent = acknowledged ? 'Appliqué' : 'En attente';
          if (acknowledged) window.setTimeout(refreshLiveScreen, 120);
        }).catch(function () {});
    }, 67);
  }

  function commitPreview() {
    window.clearTimeout(previewTimer);
    if (!previewDirty) return Promise.resolve(snapshot);
    return post('/api/onboarding/command', launcherBody('preview_launcher'))
      .then(function (data) { activePreviewSequence = data.preview && data.preview.sequence; return post('/api/onboarding/command', launcherBody('save_launcher')); })
      .then(function (data) {
      previewDirty = false;
      activePreviewId = '';
      activePreviewSequence = null;
      var acknowledged = data.device_ack === true;
      el('preview-status').className = 'ack ' + (acknowledged ? 'applied' : 'pending');
      el('preview-status').textContent = acknowledged ? 'Validé' : 'Application en attente';
      if (!acknowledged) {
        previewDirty = true;
        activePreviewId = newPreviewId();
        showRequestError('device_not_applied');
        throw { code: 'device_not_applied', validation: true };
      }
      renderPreview();
      return data;
    });
  }

  function revertPreview() {
    window.clearTimeout(previewTimer);
    if (!activePreviewId) return Promise.resolve(snapshot);
    return post('/api/onboarding/command', launcherBody('preview_launcher'))
      .then(function (data) { activePreviewSequence = data.preview && data.preview.sequence; return post('/api/onboarding/command', { command: 'revert_launcher', preview_id: activePreviewId, preview_sequence: activePreviewSequence }); })
      .then(function (data) {
      previewDirty = false;
      activePreviewId = '';
      activePreviewSequence = null;
      applySnapshot(data, false);
      var acknowledged = data.device_ack === true;
      el('preview-status').className = 'ack ' + (acknowledged ? 'applied' : 'pending');
      el('preview-status').textContent = acknowledged ? 'Restauré' : 'Restauration en attente';
      if (!acknowledged) showRequestError('device_not_applied');
      return data;
    });
  }

  function setCheck(kind, stage, state, label) {
    var row = document.querySelector('#' + kind + '-test-status [data-check="' + stage + '"]');
    if (!row) return;
    row.className = state;
    row.querySelector('strong').textContent = label;
    row.parentNode.setAttribute('aria-busy', state === 'testing' ? 'true' : 'false');
  }

  function runCheck(kind, stage, body) {
    setCheck(kind, stage, 'testing', 'Test…');
    return post('/api/test-' + (kind === 'gemini' ? 'voice' : kind), Object.assign({}, body, { stage: stage }))
      .then(function (data) { setCheck(kind, stage, 'ok', 'Validé'); return data; })
      .catch(function (error) { setCheck(kind, stage, 'failed', 'Échec'); showRequestError(error.code); throw error; });
  }

  function saveHa() {
    var body = { ha_url: el('ha-url').value.trim(), ha_token: el('ha-token').value.trim() };
    clearFieldError('ha-url'); clearFieldError('ha-token');
    if (!/^https?:\/\/.+/.test(body.ha_url)) return Promise.reject(showFieldError('ha-url', 'invalid_ha_url'));
    if (!body.ha_token && !snapshot.ha_configured) return Promise.reject(showFieldError('ha-token', 'home_assistant_token_required'));
    hide(el('ha-test-status'), false);
    var tests = runCheck('ha', 'host', body).then(function () { return runCheck('ha', 'port', body); });
    if (body.ha_token) tests = tests.then(function () { return runCheck('ha', 'token', body); });
    return tests.then(function () { return post('/api/config/ha', body); });
  }

  function saveMqtt() {
    var body = { broker_host: el('broker-host').value.trim(), broker_port: Number(el('broker-port').value), username: el('mqtt-username').value.trim(), password: el('mqtt-password').value, device_name: el('device-name').value.trim() };
    ['broker-host', 'broker-port', 'mqtt-username'].forEach(clearFieldError);
    if (!body.broker_host) return Promise.reject(showFieldError('broker-host', 'invalid_broker_host'));
    if (!Number.isInteger(body.broker_port) || body.broker_port < 1 || body.broker_port > 65535) return Promise.reject(showFieldError('broker-port', 'invalid_mqtt_port'));
    if (body.password && !body.username) return Promise.reject(showFieldError('mqtt-username', 'mqtt_username_required'));
    hide(el('mqtt-test-status'), false);
    return runCheck('mqtt', 'host', body).then(function () { return runCheck('mqtt', 'port', body); }).then(function () { return runCheck('mqtt', 'auth', body); }).then(function () { return post('/api/config/mqtt', body); });
  }

  function saveGemini() {
    var body = { voice_enabled: el('gemini-enabled').checked, voice_gemini_key: el('gemini-key').value.trim(), voice_gemini_model: el('gemini-model').value, voice_gemini_voice: el('gemini-voice').value, voice_gemini_prompt: el('gemini-prompt').value, voice_barge_in: el('gemini-barge-in').checked, voice_wake_word: el('gemini-wake-word').value, voice_threshold: Number(el('gemini-threshold').value), voice_idle_seconds: Number(el('gemini-idle').value), voice_daily_limit: Number(el('gemini-daily-limit').value) };
    if (!body.voice_enabled) return post('/api/config/voice', body);
    if (!body.voice_gemini_key && snapshot.gemini_configured) return post('/api/config/voice', body);
    clearFieldError('gemini-key');
    if (!body.voice_gemini_key) return Promise.reject(showFieldError('gemini-key', 'voice_key_required'));
    hide(el('gemini-test-status'), false);
    return runCheck('gemini', 'key', body).then(function (data) {
      if (data.models && data.models.length) { var currentModel = body.voice_gemini_model; el('gemini-model').innerHTML = ''; data.models.forEach(function (model) { var option = document.createElement('option'); option.value = model; option.textContent = model; el('gemini-model').appendChild(option); }); setSelectValue(el('gemini-model'), currentModel || data.models[0]); body.voice_gemini_model = el('gemini-model').value; }
      return runCheck('gemini', 'model', body);
    }).then(function () { return post('/api/config/voice', body); });
  }

  function loadHaCatalog() {
    el('ha-catalog-state').textContent = 'Chargement…';
    return request('/api/ha/catalog').then(function (data) { haCatalog = data; hide(el('ha-catalog'), false); el('ha-catalog-state').textContent = data.stale ? 'Données en cache' : ''; renderHaCatalog(); }).catch(function (error) { el('ha-catalog-state').textContent = errors[error.code] || 'Catalogue indisponible. Réessayez.'; throw error; });
  }

  function renderHaCatalog() {
    var disabled = new Set(snapshot.disabled_ha_integrations || []);
    el('ha-integrations-table').innerHTML = '';
    (haCatalog.integrations || []).forEach(function (integration) { var row = document.createElement('tr'); row.innerHTML = '<td><input type="checkbox" data-integration=""></td><td><span class="entity-cell"><img alt="" width="20" height="20"><span></span></span></td><td class="tabular"></td>'; var checkbox = row.querySelector('input'); checkbox.dataset.integration = integration.id; checkbox.checked = !disabled.has(integration.id); row.querySelector('img').src = apiUrl(integration.brand_url); row.querySelector('span span').textContent = integration.name; row.lastElementChild.textContent = integration.entity_count; el('ha-integrations-table').appendChild(row); });
    var domains = Array.from(new Set((haCatalog.entities || []).map(function (entity) { return entity.domain; }))).sort();
    var selectedDomain = el('ha-domain-filter').value; el('ha-domain-filter').innerHTML = '<option value="">Tous les types</option>'; domains.forEach(function (domain) { var option = document.createElement('option'); option.value = domain; option.textContent = domain; el('ha-domain-filter').appendChild(option); }); el('ha-domain-filter').value = selectedDomain;
    var query = el('ha-search').value.toLowerCase(); var availability = el('ha-availability-filter').value;
    var rules = new Map((snapshot.pill_rules || []).map(function (rule) { return [rule.entity_id, rule]; }));
    var filtered = (haCatalog.entities || []).filter(function (entity) { return (!query || (entity.name + ' ' + entity.entity_id).toLowerCase().indexOf(query) >= 0) && (!selectedDomain || entity.domain === selectedDomain) && (!availability || (availability === 'available') === entity.available); });
    el('ha-entities-table').innerHTML = ''; el('ha-entity-count').textContent = filtered.length + ' / ' + (haCatalog.entities || []).length;
    filtered.forEach(function (entity) { var rule = rules.get(entity.entity_id); var row = document.createElement('tr'); row.dataset.entity = entity.entity_id; row.innerHTML = '<td><input class="entity-enabled" type="checkbox" aria-label="Activer"></td><td><strong></strong><small class="mono"></small></td><td></td><td><span class="status-label"></span></td><td><select class="entity-pin" aria-label="Épinglage"><option value="">Non épinglée</option><option value="primary">Principale</option><option value="secondary">Secondaire</option></select></td>'; row.querySelector('.entity-enabled').checked = !!(rule && rule.enabled); row.querySelector('strong').textContent = entity.name; row.querySelector('small').textContent = entity.entity_id; row.children[2].textContent = entity.domain; row.querySelector('.status-label').textContent = entity.available ? entity.state : 'Indisponible'; var key = 'device:' + entity.entity_id; var pinIndex = (snapshot.home && snapshot.home.pinned_order || []).indexOf(key); row.querySelector('.entity-pin').value = pinIndex < 0 ? '' : pinIndex < 3 ? 'primary' : 'secondary'; el('ha-entities-table').appendChild(row); });
    renderCameras();
  }

  function renderCameras() {
    var cameras = (haCatalog.entities || []).filter(function (entity) { return entity.domain === 'camera'; }); hide(el('ha-cameras-block'), !cameras.length); el('ha-cameras-table').innerHTML = ''; var prefs = snapshot.cameras || { hidden: [], order: [] };
    cameras.forEach(function (camera, index) { var row = document.createElement('tr'); row.dataset.camera = camera.entity_id; row.innerHTML = '<td><input class="camera-visible" type="checkbox"></td><td></td><td><input class="camera-main" type="radio" name="main-camera"></td><td><input class="camera-order tabular" type="number" min="1"></td>'; row.querySelector('.camera-visible').checked = (prefs.hidden || []).indexOf(camera.entity_id) < 0; row.children[1].textContent = camera.name; row.querySelector('.camera-main').checked = prefs.main_camera === camera.entity_id; var saved = (prefs.order || []).indexOf(camera.entity_id); row.querySelector('.camera-order').value = (saved < 0 ? index : saved) + 1; el('ha-cameras-table').appendChild(row); });
    el('camera-pill').checked = (snapshot.home && snapshot.home.pinned_order || []).indexOf('special:cameras') >= 0;
  }

  function normalizeHomeDraft() {
    if (!homeDraft) homeDraft = JSON.parse(JSON.stringify(snapshot.home || { schema_version: 1, home_page_enabled: true, grouping_mode: 'type', pinned_order: [], home_sections: [], manual_groups: [] }));
    homeDraft.home_sections = homeDraft.home_sections || [];
    homeDraft.manual_groups = homeDraft.manual_groups || [];
    return homeDraft;
  }

  function renderHomeStructure() {
    var draft = normalizeHomeDraft();
    var sections = draft.home_sections.slice().sort(function (a, b) { return a.order - b.order; });
    el('home-sections-count').textContent = sections.length;
    el('home-sections-table').innerHTML = '';
    sections.forEach(function (section, index) {
      var row = document.createElement('tr'); row.dataset.section = section.section_id;
      row.innerHTML = '<td><input class="section-visible" type="checkbox" aria-label="Visibilité"></td><td><code></code></td><td><span class="order-buttons"><button type="button" class="text-button section-up" aria-label="Monter la section">Monter</button><button type="button" class="text-button section-down" aria-label="Descendre la section">Descendre</button></span></td><td><input class="section-items mono" type="text" aria-label="Ordre des items" placeholder="device:light.kitchen, area:kitchen"></td>';
      row.querySelector('.section-visible').checked = section.visible !== false;
      row.querySelector('code').textContent = section.section_id;
      row.querySelector('.section-items').value = (section.item_order || []).join(', ');
      row.querySelector('.section-up').disabled = index === 0;
      row.querySelector('.section-down').disabled = index === sections.length - 1;
      el('home-sections-table').appendChild(row);
    });
    el('manual-groups-count').textContent = draft.manual_groups.length;
    hide(el('manual-groups-empty'), draft.manual_groups.length > 0);
    el('manual-groups-table').innerHTML = '';
    draft.manual_groups.forEach(function (group, groupIndex) {
      var row = document.createElement('tr'); row.dataset.group = group.id;
      row.innerHTML = '<td><input class="group-name" type="text" maxlength="64" aria-label="Nom du groupe"></td><td><div class="group-members"></div><div class="member-add"><select class="member-candidate" aria-label="Entité à ajouter"><option value="">Ajouter une entité</option></select><button type="button" class="text-button member-add-button">Ajouter</button></div></td><td><button type="button" class="text-button group-up" aria-label="Monter le groupe">Monter</button><button type="button" class="text-button group-down" aria-label="Descendre le groupe">Descendre</button></td><td><button type="button" class="text-button danger group-delete">Supprimer</button></td>';
      row.querySelector('.group-name').value = group.name;
      var members = row.querySelector('.group-members');
      (group.members || []).forEach(function (ref, memberIndex) {
        var id = String(ref).replace(/^device:/, ''); var item = document.createElement('div'); item.className = 'member-row'; item.dataset.member = id;
        item.innerHTML = '<code></code><span><button type="button" class="text-button member-up" aria-label="Monter le membre">↑</button><button type="button" class="text-button member-down" aria-label="Descendre le membre">↓</button><select class="member-target" aria-label="Groupe de destination"><option value="">Déplacer vers</option></select><button type="button" class="text-button member-move">Déplacer</button><button type="button" class="text-button danger member-remove" aria-label="Retirer le membre">Retirer</button></span>'; item.querySelector('code').textContent = id; item.querySelector('.member-up').disabled = memberIndex === 0; item.querySelector('.member-down').disabled = memberIndex === group.members.length - 1; draft.manual_groups.filter(function (target) { return target.id !== group.id; }).forEach(function (target) { var option = document.createElement('option'); option.value = target.id; option.textContent = target.name; item.querySelector('.member-target').appendChild(option); }); members.appendChild(item);
      });
      var candidate = row.querySelector('.member-candidate');
      (haCatalog.entities || []).filter(function (entity) { return (group.members || []).indexOf('device:' + entity.entity_id) < 0; }).forEach(function (entity) { var option = document.createElement('option'); option.value = entity.entity_id; option.textContent = entity.name + ' · ' + entity.entity_id; candidate.appendChild(option); });
      row.querySelector('.group-up').disabled = groupIndex === 0; row.querySelector('.group-down').disabled = groupIndex === draft.manual_groups.length - 1;
      el('manual-groups-table').appendChild(row);
    });
  }

  function syncHomeDraftFromDom() {
    var draft = normalizeHomeDraft();
    var bySection = new Map(draft.home_sections.map(function (section) { return [section.section_id, section]; }));
    all('#home-sections-table tr').forEach(function (row, index) { var section = bySection.get(row.dataset.section); if (!section) return; section.visible = row.querySelector('.section-visible').checked; section.order = index; section.item_order = row.querySelector('.section-items').value.split(',').map(function (value) { return value.trim(); }).filter(Boolean); });
    var byGroup = new Map(draft.manual_groups.map(function (group) { return [group.id, group]; }));
    all('#manual-groups-table tr').forEach(function (row) { var group = byGroup.get(row.dataset.group); if (!group) return; group.name = row.querySelector('.group-name').value.trim(); group.members = Array.prototype.slice.call(row.querySelectorAll('.member-row')).map(function (member) { return 'device:' + member.dataset.member; }); });
    homeDraftDirty = true;
  }

  function moveDraftItem(list, index, direction) { var target = index + direction; if (target < 0 || target >= list.length) return; var item = list[index]; list.splice(index, 1); list.splice(target, 0, item); homeDraftDirty = true; renderHomeStructure(); }

  function saveHome() {
    var disabled = all('[data-integration]').filter(function (node) { return !node.checked; }).map(function (node) { return node.dataset.integration; });
    var existing = new Map((snapshot.pill_rules || []).map(function (rule) { return [rule.entity_id, rule]; })); var rules = Array.from(existing.values());
    all('#ha-entities-table tr').forEach(function (row) { var id = row.dataset.entity; var rule = existing.get(id); var enabled = row.querySelector('.entity-enabled').checked; if (rule) rule.enabled = enabled; else if (enabled) rules.push({ entity_id: id, kind: 'GENERIC', label: row.querySelector('strong').textContent, enabled: true, priority_boost: 0, related_entity_ids: [] }); });
    syncHomeDraftFromDom(); var home = normalizeHomeDraft(); home.home_page_enabled = el('home-enabled').checked; home.grouping_mode = el('home-grouping').value; var primary = [], secondary = []; all('#ha-entities-table tr').forEach(function (row) { var pin = row.querySelector('.entity-pin').value; if (pin === 'primary') primary.push('device:' + row.dataset.entity); if (pin === 'secondary') secondary.push('device:' + row.dataset.entity); }); home.pinned_order = primary.slice(0, 3).concat(secondary.slice(0, 6)); if (el('camera-pill').checked) home.pinned_order.push('special:cameras');
    var cameraRows = all('#ha-cameras-table tr').sort(function (a, b) { return Number(a.querySelector('.camera-order').value) - Number(b.querySelector('.camera-order').value); }); var cameras = { schema_version: 1, hidden: cameraRows.filter(function (row) { return !row.querySelector('.camera-visible').checked; }).map(function (row) { return row.dataset.camera; }), order: cameraRows.map(function (row) { return row.dataset.camera; }), main_camera: (document.querySelector('.camera-main:checked') || {}).closest ? document.querySelector('.camera-main:checked').closest('tr').dataset.camera : null, default_mode: el('camera-mode').value };
    return post('/api/config/home', { disabled_integrations: disabled, pill_rules: rules, home: home, cameras: cameras }).then(function (data) { homeDraftDirty = false; applySnapshot(data, false); renderHaCatalog(); return data; });
  }

  function saveProviders() {
    providerState.ha = el('provider-ha').checked;
    providerState.mqtt = el('provider-mqtt').checked;
    providerState.gemini = el('provider-gemini').checked;
    hide(el('no-providers-state'), providerState.ha || providerState.mqtt || providerState.gemini);
    return post('/api/onboarding/command', {
      command: 'select_providers',
      ha_selected: providerState.ha,
      mqtt_selected: providerState.mqtt,
      gemini_selected: providerState.gemini
    }).then(function (data) { providerDraftDirty = false; applySnapshot(data, false); return data; });
  }

  function backgroundBody() {
    var selectedAlbums = Array.prototype.slice.call(el('immich-albums').selectedOptions || []).map(function (option) { return option.value; });
    return {
      command: 'save_background',
      background_mode: selectedBackground(),
      background_opacity: Number(el('background-opacity').value),
      immich_url: el('immich-url').value.trim(),
      immich_api_key: el('immich-key').value.trim(),
      immich_album_ids: selectedAlbums,
      immich_allow_insecure: el('immich-insecure').checked,
      immich_shuffle: el('immich-shuffle').checked,
      immich_refresh_minutes: Number(el('immich-refresh').value),
      immich_cadence_seconds: Number(el('immich-cadence').value)
    };
  }

  function readImage(file) {
    if (!file) return Promise.resolve();
    if (['image/jpeg', 'image/png', 'image/webp'].indexOf(file.type) < 0) return Promise.reject(showFieldError('background-file', 'invalid_image_type'));
    if (file.size > 8 * 1024 * 1024) return Promise.reject(showFieldError('background-file', 'image_too_large'));
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onerror = function () { reject({ code: 'invalid_image' }); };
      reader.onload = function () {
        var base64 = String(reader.result).split(',')[1] || '';
        post('/api/background/upload', { mime: file.type, base64: base64, background_opacity: Number(el('background-opacity').value) })
          .then(function (data) { el('background-upload-state').textContent = file.name + ' · ' + Math.round(file.size / 1024) + ' Ko'; hide(el('background-upload-state'), false); applySnapshot(data, false); resolve(data); })
          .catch(reject);
      };
      reader.readAsDataURL(file);
    });
  }

  function saveBackground() {
    var file = el('background-file').files && el('background-file').files[0];
    return readImage(file).then(function () { return post('/api/config/background', backgroundBody()); }).then(function () { return commitPreview(); });
  }

  function testImmich() {
    var body = backgroundBody();
    el('test-immich').disabled = true; el('test-immich').setAttribute('aria-busy', 'true');
    return post('/api/test-immich', body).then(function (data) {
      var select = el('immich-albums'); select.innerHTML = '';
      (data.albums || []).forEach(function (album) { var option = document.createElement('option'); option.value = album.id; option.textContent = album.label + ' · ' + album.asset_count; select.appendChild(option); });
      if (!(data.albums || []).length) { var option = document.createElement('option'); option.disabled = true; option.textContent = 'Aucun album'; select.appendChild(option); }
      if (data.thumbnail && data.thumbnail.base64) {
        var image = el('preview-background-image');
        image.src = 'data:' + data.thumbnail.mime + ';base64,' + data.thumbnail.base64;
        hide(image, false); hide(el('preview-unavailable'), true);
      }
      return data;
    }).finally(function () { el('test-immich').disabled = readOnly; el('test-immich').removeAttribute('aria-busy'); });
  }

  function saveClock() {
    return commitPreview().then(function () { return post('/api/config/clock', clockBody()); });
  }

  function saveApps() {
    var hidden = (launcher.apps || []).filter(function (app) { return app.hidden && !app.protected; }).map(function (app) { return app.package; });
    var order = (launcher.apps || []).map(function (app) { return app.package; });
    return post('/api/config/apps', {
      hidden_apps: hidden, app_order: order, icon_pack: el('icon-pack').value,
      notification_dots: el('notification-dots').checked,
      home_assistant_package: el('ha-companion-app').value,
      tap_app_package: el('tap-app').value
    });
  }

  function saveBehavior() {
    return post('/api/config/behavior', {
      keep_screen_on: el('keep-screen-on').checked,
      screen_timeout_enabled: el('screen-timeout-enabled').checked,
      screen_timeout_minutes: Number(el('screen-timeout-minutes').value),
      auto_return_enabled: el('auto-return-enabled').checked,
      auto_return_delay_seconds: Number(el('auto-return-delay').value),
      gestures_seen: el('gestures-seen').checked
    });
  }

  function updateReview() {
    el('review-display').textContent = 'Prêt · ' + Number(el('grid-scale').value).toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    el('review-background').textContent = 'Prêt · ' + selectedBackground() + ' · ' + Math.round(Number(el('background-opacity').value) * 100) + ' %';
    el('review-ha').textContent = providerState.ha ? (snapshot.ha_configured ? 'Prêt' : 'Action requise') : 'Ignoré';
    el('review-mqtt').textContent = providerState.mqtt ? (snapshot.mqtt_configured ? 'Prêt' : 'Action requise') : 'Ignoré';
    el('review-gemini').textContent = providerState.gemini ? (snapshot.gemini_configured ? 'Prêt' : 'Action requise') : 'Ignoré';
  }

  function complete() {
    var warningVisible = !el('defaults-warning').classList.contains('hidden');
    return post('/api/onboarding/complete', { confirm_finish: true, defaults_warning_accepted: !warningVisible || el('accept-defaults').checked }).then(showComplete);
  }

  function showComplete() {
    hide(el('config-form'), true); hide(el('loading-state'), true); hide(el('saved-state'), false);
    el('title-complete').focus();
  }

  function next() {
    if (busy || readOnly) return;
    busy = true; el('next').disabled = true; el('config-form').setAttribute('aria-busy', 'true'); hide(el('request-error'), true);
    var action = current === 'system' ? Promise.resolve()
      : current === 'display' ? commitPreview()
      : current === 'background' ? saveBackground()
      : current === 'clock' ? saveClock()
      : current === 'apps' ? saveApps()
      : current === 'behavior' ? saveBehavior()
      : current === 'providers' ? saveProviders()
      : current === 'ha' ? saveHa()
      : current === 'mqtt' ? saveMqtt()
      : current === 'gemini' ? saveGemini()
      : complete();
    action.then(function () {
      if (current !== 'review') {
        var steps = activeSteps();
        showPage(steps[Math.min(steps.indexOf(current) + 1, steps.length - 1)], true);
      }
    }).catch(function () {}).finally(function () { busy = false; el('config-form').setAttribute('aria-busy', 'false'); if (!readOnly) el('next').disabled = false; });
  }

  function loadSnapshot(initial) {
    return request('/api/onboarding?session_id=' + encodeURIComponent(sessionId)).then(function (data) { applySnapshot(data, initial); syncEditorState(data); return data; });
  }

  function recoverInitial() {
    if (recoveryPromise) return recoveryPromise;
    hide(el('loading-state'), false);
    el('retry-server').disabled = true;
    el('retry-server').setAttribute('aria-busy', 'true');
    recoveryPromise = Promise.all([request('/api/config'), request('/api/onboarding?session_id=' + encodeURIComponent(sessionId)), request('/api/launcher')]).then(function (values) {
      fillConfig(values[0]);
      launcher = values[2];
      hide(el('loading-state'), true);
      hide(el('transport-warning'), false);
      if (!values[1].completed) hide(el('config-form'), false);
      applySnapshot(values[1], true);
      fillLauncherCatalog(); renderApps(); loadLauncherState();
      syncEditorState(values[1]);
      startEvents();
      return values[1];
    }).catch(function (error) {
      hide(el('loading-state'), true);
      hide(el('config-form'), true);
      markOffline();
      throw error;
    });
    recoveryPromise = recoveryPromise.finally(function () {
      el('retry-server').disabled = false;
      el('retry-server').setAttribute('aria-busy', 'false');
      recoveryPromise = null;
    });
    return recoveryPromise;
  }

  function startEvents() {
    if (demo) return;
    if (sse || pollTimer) return;
    if (!window.EventSource) return startPolling();
    sse = new EventSource(apiUrl('/api/onboarding/events?session_id=' + encodeURIComponent(sessionId)));
    sse.onmessage = function (event) {
      sseErrors = 0;
      try { var data = JSON.parse(event.data); markConnected(null); if (!busy) applySnapshot(data, false); syncEditorState(data); } catch (_) {}
    };
    sse.onerror = function () {
      sseErrors += 1;
      if (sseErrors >= 5) { sse.close(); sse = null; startPolling(); }
    };
  }

  function startPolling() {
    if (pollTimer) return;
    pollTimer = window.setInterval(function () { if (!busy) loadSnapshot(false).catch(function () {}); }, 2000);
  }

  el('config-form').addEventListener('submit', function (event) { event.preventDefault(); next(); });
  el('back').addEventListener('click', function () { var steps = activeSteps(); var index = steps.indexOf(current); if (index > 0) showPage(steps[index - 1], true); });
  el('revert').addEventListener('click', function () { revertPreview().catch(function () {}); });
  el('retry-server').addEventListener('click', function () { recoverInitial().catch(function () {}); });
  el('reload-snapshot').addEventListener('click', function () { loadSnapshot(false).then(function (data) {
    if (!data.editor_active || data.editor_owned) { hide(el('conflict-state'), true); setReadOnly(false, false); }
    else setReadOnly(true, true);
  }).catch(function () {}); });
  el('take-over').addEventListener('click', function () { post('/api/onboarding/command', { command: 'navigate', step: coordinatorStep(current) }, true).then(function () { hide(el('conflict-state'), true); setReadOnly(false, false); }).catch(function () {}); });
  el('reset-onboarding').addEventListener('click', function () { if (!window.confirm('Recommencer l’onboarding ? Les réglages actuels seront conservés.')) return; post('/api/onboarding/reset', { confirmation: 'RESET' }).then(function (data) { applySnapshot(data, true); }).catch(function () {}); });
  el('language-select').value = document.documentElement.lang;
  el('language-select').addEventListener('change', function () { var target = new URL(location.href); target.searchParams.set('lang', this.value); location.assign(target.toString()); });
  all('[data-target]').forEach(function (node) { node.addEventListener('click', function () { showPage(node.dataset.target, true); }); });
  ['provider-ha', 'provider-mqtt', 'provider-gemini'].forEach(function (id) { el(id).addEventListener('change', function () { providerDraftDirty = true; hide(el('no-providers-state'), el('provider-ha').checked || el('provider-mqtt').checked || el('provider-gemini').checked); }); });
  all('[data-scale]').forEach(function (node) { node.addEventListener('click', function () { el('grid-scale').value = node.dataset.scale; schedulePreview(); commitPreview().catch(function () {}); }); });
  el('grid-scale').addEventListener('input', schedulePreview); el('grid-scale').addEventListener('change', function () { commitPreview().catch(function () {}); });
  el('background-opacity').addEventListener('input', schedulePreview); el('background-opacity').addEventListener('change', schedulePreview);
  all('input[name="background-mode"]').forEach(function (node) { node.addEventListener('change', function () { toggleBackgroundFields(); schedulePreview(); }); });
  ['clock-font', 'clock-weight', 'clock-size', 'clock-letter-spacing', 'clock-tint', 'clock-24h', 'clock-date-format', 'clock-element-spacing'].forEach(function (id) { el(id).addEventListener('input', schedulePreview); el(id).addEventListener('change', schedulePreview); });
  all('[data-system-action]').forEach(function (button) { button.addEventListener('click', function () {
    button.disabled = true; button.textContent = 'Ouverture…';
    post('/api/system/action', { action: button.dataset.systemAction }).then(function () { window.setTimeout(loadLauncherState, 1200); }).catch(function () { button.disabled = false; button.textContent = 'Ouvrir'; });
  }); });
  el('retry-capabilities').addEventListener('click', loadLauncherState);
  el('load-ha-catalog').addEventListener('click', function () { loadHaCatalog().catch(function () {}); });
  el('save-home').addEventListener('click', function () { saveHome().catch(function () {}); });
  el('add-manual-group').addEventListener('click', function () { var name = el('manual-group-name').value.trim(); if (!name) return; var draft = normalizeHomeDraft(); draft.manual_groups.push({ id: 'group-' + Date.now().toString(36), name: name, icon: null, members: [] }); el('manual-group-name').value = ''; homeDraftDirty = true; renderHomeStructure(); });
  el('manual-group-name').addEventListener('keydown', function (event) { if (event.key === 'Enter') { event.preventDefault(); el('add-manual-group').click(); } });
  el('home-sections-table').addEventListener('click', function (event) { var button = event.target.closest('button'); if (!button) return; syncHomeDraftFromDom(); var ordered = normalizeHomeDraft().home_sections.slice().sort(function (a, b) { return a.order - b.order; }); var row = button.closest('tr'); var index = ordered.findIndex(function (section) { return section.section_id === row.dataset.section; }); moveDraftItem(ordered, index, button.classList.contains('section-up') ? -1 : 1); ordered.forEach(function (section, order) { section.order = order; }); normalizeHomeDraft().home_sections = ordered; renderHomeStructure(); });
  el('manual-groups-table').addEventListener('click', function (event) { var button = event.target.closest('button'); if (!button) return; syncHomeDraftFromDom(); var draft = normalizeHomeDraft(); var row = button.closest('tr'); var groupIndex = draft.manual_groups.findIndex(function (group) { return group.id === row.dataset.group; }); var group = draft.manual_groups[groupIndex]; if (!group) return; if (button.classList.contains('group-delete')) draft.manual_groups.splice(groupIndex, 1); else if (button.classList.contains('group-up')) moveDraftItem(draft.manual_groups, groupIndex, -1); else if (button.classList.contains('group-down')) moveDraftItem(draft.manual_groups, groupIndex, 1); else if (button.classList.contains('member-add-button')) { var id = row.querySelector('.member-candidate').value; if (id && group.members.indexOf('device:' + id) < 0) group.members.push('device:' + id); } else { var memberRow = button.closest('.member-row'); if (!memberRow) return; var memberIndex = group.members.indexOf('device:' + memberRow.dataset.member); if (button.classList.contains('member-remove')) group.members.splice(memberIndex, 1); else if (button.classList.contains('member-up')) moveDraftItem(group.members, memberIndex, -1); else if (button.classList.contains('member-down')) moveDraftItem(group.members, memberIndex, 1); else if (button.classList.contains('member-move')) { var target = draft.manual_groups.find(function (candidate) { return candidate.id === memberRow.querySelector('.member-target').value; }); if (target) { var moved = group.members.splice(memberIndex, 1)[0]; if (target.members.indexOf(moved) < 0) target.members.push(moved); } } } homeDraftDirty = true; renderHomeStructure(); });
  el('manual-groups-table').addEventListener('input', syncHomeDraftFromDom);
  el('home-sections-table').addEventListener('input', syncHomeDraftFromDom);
  ['ha-search', 'ha-domain-filter', 'ha-availability-filter'].forEach(function (id) { el(id).addEventListener(id === 'ha-search' ? 'input' : 'change', renderHaCatalog); });
  el('ha-integrations-all').addEventListener('change', function () { var checked = this.checked; all('[data-integration]').forEach(function (node) { node.checked = checked; }); });
  el('request-microphone').addEventListener('click', function () { post('/api/system/action', { action: 'microphone' }).catch(function () {}); });
  el('test-immich').addEventListener('click', function () { testImmich().catch(function () {}); });
  el('apps-search').addEventListener('input', renderApps); el('apps-filter').addEventListener('change', renderApps);
  el('reset-app-filter').addEventListener('click', function () { el('apps-search').value = ''; el('apps-filter').value = 'all'; renderApps(); });
  capabilityTimer = window.setInterval(function () { if (current === 'system' && !busy) loadLauncherState(); }, 2500);
  liveScreenTimer = window.setInterval(refreshLiveScreen, 900);
  document.addEventListener('keydown', function (event) { if (event.key === 'Escape' && previewDirty) revertPreview().catch(function () {}); });
  el('skip-ha').addEventListener('click', function () { post('/api/onboarding/command', { command: 'skip_ha' }).then(function () { providerState.ha = false; showPage(providerState.mqtt ? 'mqtt' : providerState.gemini ? 'gemini' : 'review', true); }).catch(function () {}); });
  el('skip-mqtt').addEventListener('click', function () { post('/api/onboarding/command', { command: 'skip_mqtt' }).then(function () { providerState.mqtt = false; showPage(providerState.gemini ? 'gemini' : 'review', true); }).catch(function () {}); });
  el('skip-gemini').addEventListener('click', function () {
    providerState.gemini = false;
    el('gemini-enabled').checked = false;
    post('/api/onboarding/command', { command: 'select_providers', ha_selected: providerState.ha, mqtt_selected: providerState.mqtt, gemini_selected: false })
      .then(function () { return saveGemini(); })
      .then(function () { showPage('review', true); }).catch(function () {});
  });

  if (demo) {
    snapshot = { revision: 12, step: 'GRID', grid_scale: 1, background_mode: 'neutral', background_opacity: .25, ha_configured: false, ha_skipped: false, mqtt_configured: false, mqtt_skipped: false, gemini_configured: false, warnings: ['DEFAULTS_WILL_BE_USED'] };
    fillConfig({ broker_port: 1883, device_name: 'panel-cuisine' });
    applySnapshot(snapshot, true); hide(el('loading-state'), true); hide(el('config-form'), false); hide(el('transport-warning'), false); markConnected(18);
  } else {
    recoverInitial().catch(function () {});
  }
})();
