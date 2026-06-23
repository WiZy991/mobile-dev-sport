/**
 * Кастомные выпадающие списки в стиле CRM (тёмная тема везде, не только закрытое поле).
 */
(function (global) {
    'use strict';

    const OPEN_CLASS = 'is-open';
    const ENHANCED = 'data-crm-select-enhanced';

    function selectedLabel(select) {
        const opt = select.options[select.selectedIndex];
        if (!opt) return '';
        return opt.textContent.trim();
    }

    function closeAll(except) {
        document.querySelectorAll('.crm-select.' + OPEN_CLASS).forEach((wrap) => {
            if (except && wrap === except) return;
            wrap.classList.remove(OPEN_CLASS);
            const trigger = wrap.querySelector('.crm-select__trigger');
            if (trigger) trigger.setAttribute('aria-expanded', 'false');
        });
    }

    function CrmSelect(select) {
        this.select = select;
        this.wrap = document.createElement('div');
        this.wrap.className = 'crm-select';
        if (select.classList.contains('form-select-sm')) {
            this.wrap.classList.add('crm-select--sm');
        }
        if (select.style.minWidth) this.wrap.style.minWidth = select.style.minWidth;
        if (select.style.width) this.wrap.style.width = select.style.width;
        if (select.style.maxWidth) this.wrap.style.maxWidth = select.style.maxWidth;

        select.classList.add('crm-native-select');
        select.setAttribute(ENHANCED, '1');
        select.setAttribute('tabindex', '-1');
        select.setAttribute('aria-hidden', 'true');

        const parent = select.parentNode;
        parent.insertBefore(this.wrap, select);
        this.wrap.appendChild(select);

        this.trigger = document.createElement('button');
        this.trigger.type = 'button';
        this.trigger.className = 'crm-select__trigger';
        this.trigger.setAttribute('aria-haspopup', 'listbox');
        this.trigger.setAttribute('aria-expanded', 'false');

        this.label = document.createElement('span');
        this.label.className = 'crm-select__label';
        this.chevron = document.createElement('i');
        this.chevron.className = 'bi bi-chevron-down crm-select__chevron';
        this.trigger.appendChild(this.label);
        this.trigger.appendChild(this.chevron);

        this.menu = document.createElement('div');
        this.menu.className = 'crm-select__menu';
        this.menu.setAttribute('role', 'listbox');

        this.wrap.appendChild(this.trigger);
        this.wrap.appendChild(this.menu);

        this.trigger.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            if (select.disabled) return;
            if (this.wrap.classList.contains(OPEN_CLASS)) {
                this.close();
            } else {
                this.open();
            }
        });

        this.select.addEventListener('change', () => this.refresh());
        this.hookValueSetter();

        this.observer = new MutationObserver(() => this.refresh());
        this.observer.observe(select, {
            attributes: true,
            attributeFilter: ['disabled'],
            childList: true,
            subtree: true,
        });

        this.refresh();
        select._crmSelect = this;
    }

    CrmSelect.prototype.hookValueSetter = function () {
        const select = this.select;
        if (select._crmValueHooked) return;
        select._crmValueHooked = true;

        const valueDesc = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value');
        const indexDesc = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'selectedIndex');
        const self = this;

        Object.defineProperty(select, 'value', {
            configurable: true,
            get() {
                return valueDesc.get.call(this);
            },
            set(v) {
                valueDesc.set.call(this, v);
                self.refresh();
            },
        });

        Object.defineProperty(select, 'selectedIndex', {
            configurable: true,
            get() {
                return indexDesc.get.call(this);
            },
            set(v) {
                indexDesc.set.call(this, v);
                self.refresh();
            },
        });
    };

    CrmSelect.prototype.buildMenu = function () {
        const select = this.select;
        this.menu.innerHTML = '';

        Array.from(select.options).forEach((opt) => {
            const item = document.createElement('button');
            item.type = 'button';
            item.className = 'crm-select__option';
            item.setAttribute('role', 'option');
            item.textContent = opt.textContent.trim();
            item.dataset.value = opt.value;

            if (opt.disabled) {
                item.classList.add('is-disabled');
                item.disabled = true;
            }
            if (opt.selected) {
                item.classList.add('is-selected');
                item.setAttribute('aria-selected', 'true');
            }

            item.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                if (opt.disabled) return;
                select.value = opt.value;
                select.dispatchEvent(new Event('change', { bubbles: true }));
                this.refresh();
                this.close();
            });

            this.menu.appendChild(item);
        });
    };

    CrmSelect.prototype.refresh = function () {
        const select = this.select;
        this.label.textContent = selectedLabel(select) || '\u00a0';
        this.wrap.classList.toggle('crm-select--disabled', select.disabled);
        this.trigger.disabled = select.disabled;
        this.buildMenu();
    };

    CrmSelect.prototype.open = function () {
        closeAll(this.wrap);
        this.wrap.classList.add(OPEN_CLASS);
        this.trigger.setAttribute('aria-expanded', 'true');

        const rect = this.trigger.getBoundingClientRect();
        const menuMax = 240;
        const spaceBelow = window.innerHeight - rect.bottom - 8;
        const spaceAbove = rect.top - 8;
        const openUp = spaceBelow < Math.min(menuMax, this.menu.scrollHeight || menuMax) && spaceAbove > spaceBelow;

        this.wrap.classList.toggle('crm-select--drop-up', openUp);
        this.menu.style.maxHeight = Math.max(120, openUp ? spaceAbove : spaceBelow) + 'px';

        const selected = this.menu.querySelector('.crm-select__option.is-selected');
        if (selected) {
            selected.scrollIntoView({ block: 'nearest' });
        }
    };

    CrmSelect.prototype.close = function () {
        this.wrap.classList.remove(OPEN_CLASS);
        this.wrap.classList.remove('crm-select--drop-up');
        this.trigger.setAttribute('aria-expanded', 'false');
    };

    function shouldEnhance(select) {
        if (!select || select.tagName !== 'SELECT') return false;
        if (select.hasAttribute(ENHANCED)) return false;
        if (select.hasAttribute('data-crm-native-select')) return false;
        if (select.multiple) return false;
        if (select.size > 1) return false;
        return true;
    }

    function enhance(select) {
        if (!shouldEnhance(select)) return select._crmSelect || null;
        return new CrmSelect(select);
    }

    function enhanceAll(root) {
        const scope = root && root.querySelectorAll ? root : document;
        scope.querySelectorAll('select:not([' + ENHANCED + '])').forEach((el) => {
            if (shouldEnhance(el)) enhance(el);
        });
    }

    document.addEventListener('click', () => closeAll());
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeAll();
    });
    window.addEventListener('resize', () => closeAll());
    window.addEventListener('scroll', () => closeAll(), true);

    document.addEventListener('DOMContentLoaded', () => enhanceAll(document));

    const mo = new MutationObserver((mutations) => {
        mutations.forEach((m) => {
            m.addedNodes.forEach((node) => {
                if (node.nodeType !== 1) return;
                if (node.tagName === 'SELECT') enhance(node);
                else enhanceAll(node);
            });
        });
    });
    mo.observe(document.documentElement, { childList: true, subtree: true });

    global.crmEnhanceSelects = enhanceAll;
    global.crmEnhanceSelect = enhance;
})(window);
