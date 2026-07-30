/**
 * AJAX client picker for CRM admin forms.
 * Markup: .crm-client-picker[data-search-url] with hidden input + .crm-client-picker__input + .crm-client-picker__menu
 * data-require-selection="1" — block submit until a client is chosen from search.
 */
(function (global) {
    'use strict';

    function initPicker(root) {
        if (!root || root.dataset.crmPickerReady === '1') {
            return;
        }
        var url = root.getAttribute('data-search-url');
        var hidden = root.querySelector('input[type="hidden"]');
        var input = root.querySelector('.crm-client-picker__input');
        var menu = root.querySelector('.crm-client-picker__menu');
        if (!url || !hidden || !input || !menu) {
            return;
        }
        root.dataset.crmPickerReady = '1';

        var timer = null;
        var seq = 0;
        var pickedLabel = input.value || '';

        function positionMenu() {
            var rect = input.getBoundingClientRect();
            var maxH = 260;
            var spaceBelow = window.innerHeight - rect.bottom - 8;
            var spaceAbove = rect.top - 8;
            var openUp = spaceBelow < 160 && spaceAbove > spaceBelow;
            var height = Math.max(120, Math.min(maxH, openUp ? spaceAbove : spaceBelow));

            menu.style.position = 'fixed';
            menu.style.left = Math.max(8, rect.left) + 'px';
            menu.style.width = Math.max(rect.width, 240) + 'px';
            menu.style.zIndex = '10060';
            menu.style.maxHeight = height + 'px';
            if (openUp) {
                menu.style.top = 'auto';
                menu.style.bottom = (window.innerHeight - rect.top + 4) + 'px';
            } else {
                menu.style.bottom = 'auto';
                menu.style.top = (rect.bottom + 4) + 'px';
            }
        }

        function showMenu() {
            if (menu.parentNode !== document.body) {
                document.body.appendChild(menu);
            }
            menu.hidden = false;
            menu.classList.add('crm-client-picker__menu--portal');
            positionMenu();
        }

        function hideMenu() {
            menu.hidden = true;
            menu.classList.remove('crm-client-picker__menu--portal');
            menu.innerHTML = '';
            menu.style.position = '';
            menu.style.left = '';
            menu.style.width = '';
            menu.style.top = '';
            menu.style.bottom = '';
            menu.style.zIndex = '';
            menu.style.maxHeight = '';
            if (menu.parentNode === document.body) {
                root.appendChild(menu);
            }
        }

        function selectItem(item) {
            hidden.value = String(item.id);
            pickedLabel = item.label || item.name || '';
            input.value = pickedLabel;
            hideMenu();
            root.dispatchEvent(new CustomEvent('crm-client-picked', { detail: item, bubbles: true }));
        }

        function render(items) {
            menu.innerHTML = '';
            if (!items.length) {
                var empty = document.createElement('div');
                empty.className = 'crm-client-picker__empty';
                empty.textContent = 'Ничего не найдено';
                menu.appendChild(empty);
                showMenu();
                return;
            }
            items.forEach(function (item) {
                var btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'crm-client-picker__option';
                btn.textContent = item.label || item.name;
                if (!item.ready) {
                    btn.classList.add('is-warn');
                }
                btn.addEventListener('mousedown', function (e) {
                    e.preventDefault();
                    selectItem(item);
                });
                menu.appendChild(btn);
            });
            showMenu();
        }

        function search(q) {
            var my = ++seq;
            fetch(url + '?q=' + encodeURIComponent(q), {
                headers: { Accept: 'application/json' },
                credentials: 'same-origin'
            }).then(function (r) {
                if (!r.ok) throw new Error('search failed');
                return r.json();
            }).then(function (data) {
                if (my !== seq) return;
                render((data && data.items) ? data.items : []);
            }).catch(function () {
                if (my !== seq) return;
                hideMenu();
            });
        }

        input.addEventListener('input', function () {
            var q = input.value.trim();
            if (input.value !== pickedLabel) {
                hidden.value = '';
                pickedLabel = '';
            }
            clearTimeout(timer);
            if (q.length < 2) {
                hideMenu();
                return;
            }
            timer = setTimeout(function () { search(q); }, 250);
        });

        input.addEventListener('focus', function () {
            var q = input.value.trim();
            if (q.length >= 2 && !hidden.value) {
                search(q);
            }
        });

        document.addEventListener('click', function (e) {
            if (root.contains(e.target) || menu.contains(e.target)) return;
            hideMenu();
        });
        window.addEventListener('resize', function () {
            if (!menu.hidden) positionMenu();
        });
        window.addEventListener('scroll', function () {
            if (!menu.hidden) positionMenu();
        }, true);

        var form = root.closest('form');
        if (form && root.getAttribute('data-require-selection') === '1') {
            form.addEventListener('submit', function (e) {
                if (!hidden.value) {
                    e.preventDefault();
                    input.focus();
                    alert('Выберите клиента из списка поиска.');
                }
            });
        }

        root._crmSetClient = function (id, label) {
            hidden.value = id ? String(id) : '';
            pickedLabel = label || '';
            input.value = pickedLabel;
        };
    }

    function initAll(scope) {
        (scope || document).querySelectorAll('.crm-client-picker').forEach(initPicker);
    }

    function setClient(rootOrId, id, label) {
        var root = typeof rootOrId === 'string' ? document.getElementById(rootOrId) : rootOrId;
        if (!root) return;
        initPicker(root);
        if (root._crmSetClient) {
            root._crmSetClient(id, label);
        }
    }

    global.CrmClientPicker = { initAll: initAll, init: initPicker, set: setClient };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { initAll(); });
    } else {
        initAll();
    }
})(window);
