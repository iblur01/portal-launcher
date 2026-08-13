(function () {
  'use strict';
  var form = document.getElementById('access-form');
  var input = document.getElementById('token');
  var error = document.getElementById('access-error');
  if (document.querySelector('meta[name="portal-invalid-code"]').content === 'true') {
    error.classList.remove('hidden'); input.setAttribute('aria-invalid', 'true'); input.setAttribute('aria-describedby', 'access-error');
  }
  input.addEventListener('input', function () {
    var value = input.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8);
    input.value = value.length > 4 ? value.slice(0, 4) + '-' + value.slice(4) : value;
    error.classList.add('hidden'); input.removeAttribute('aria-invalid');
  });
  form.addEventListener('submit', function (event) {
    if (input.value.replace(/[^A-Z0-9]/g, '').length !== 8) {
      event.preventDefault(); error.textContent = 'Le code contient 8 caractères.'; error.classList.remove('hidden'); input.setAttribute('aria-invalid', 'true'); input.focus();
    }
  });
})();
