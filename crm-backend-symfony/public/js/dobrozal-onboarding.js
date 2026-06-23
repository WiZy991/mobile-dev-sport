/**
 * Dobrozal CRM tour — spotlight on real UI + Zalka comic bubble (ASCII-only strings from data-*).
 */
(function (global) {
    'use strict';

    const STORAGE_PREFIX = 'dobrozal_onboarding_';

    function readLabels(el) {
        const d = el.dataset;
        return {
            continue: d.dzLblContinue || 'Next',
            tap: d.dzLblTap || 'Tap highlighted',
            question: d.dzLblQuestion || 'Question',
            correct: d.dzLblCorrect || 'OK',
            excellent: d.dzLblExcellent || 'OK',
            wrong: d.dzLblWrong || 'Wrong',
            tryAgain: d.dzLblTryAgain || 'Try again',
            heartsOut: d.dzLblHeartsOut || '',
            proud: d.dzLblProud || 'is proud!',
            continuePath: d.dzLblContinuePath || 'Next lesson',
            mascot: d.dzLblMascot || 'Zalka',
            lessonDone: d.dzLblLessonDone || 'Done!',
            exitTour: d.dzLblExitTour || 'Exit training?',
            lessonStart: d.dzLblLessonStart || 'Start',
            lessonDoneMark: '\u2713',
            resetConfirm: 'Reset?',
        };
    }

    function currentSection() {
        return document.body.dataset.dzSection || '';
    }

    function sectionUrl(section) {
        const nav = document.querySelector('[data-dz-nav="' + section + '"]');
        if (nav && nav.href) return nav.href;
        if (section === 'dashboard') return '/admin';
        return '/admin/' + section;
    }

    function parseMascotFaces() {
        const el = document.getElementById('dz-mascot-faces-json');
        if (!el) return null;
        try {
            return JSON.parse((el.textContent || '').trim() || '{}');
        } catch {
            return null;
        }
    }

    function parseQuest() {
        const el = document.getElementById('dz-quest-json-global')
            || document.getElementById('dz-quest-json')
            || document.querySelector('[data-dz-quest-json]');
        if (!el) return null;
        try {
            return JSON.parse((el.textContent || '').trim() || '{}');
        } catch (e) {
            console.error('Dobrozal tour: JSON error', e);
            return null;
        }
    }

    class DobrozalTour {
        constructor(root, quest, userId) {
            this.root = root;
            this.quest = quest;
            this.userId = userId;
            this.labels = readLabels(root);
            this.heartsMax = quest.heartsMax || 5;
            this.mascotFaces = this.loadMascotFaces();
            this.state = this.loadState();
            this.currentLesson = null;
            this.currentStepIndex = 0;
            this.clickHandlers = [];
            this.resizeHandler = null;
            this.cacheDom();
            this.bindEvents();
            if (this.state.activeLesson) {
                const lesson = this.lessonById(this.state.activeLesson);
                if (lesson) {
                    this.currentLesson = lesson;
                    this.currentStepIndex = this.state.activeStep || 0;
                    this.runStep();
                } else {
                    this.clearActive();
                }
            }
        }

        loadMascotFaces() {
            const parsed = parseMascotFaces();
            const fallback = {
                neutral: [
                    '/img/mascot/heads/zalka-head-01.png',
                    '/img/mascot/heads/zalka-head-05.png',
                    '/img/mascot/heads/zalka-head-09.png',
                    '/img/mascot/heads/zalka-head-13.png',
                ],
                happy: [
                    '/img/mascot/heads/zalka-head-02.png',
                    '/img/mascot/heads/zalka-head-06.png',
                    '/img/mascot/heads/zalka-head-10.png',
                    '/img/mascot/heads/zalka-head-14.png',
                ],
                excited: [
                    '/img/mascot/heads/zalka-head-03.png',
                    '/img/mascot/heads/zalka-head-07.png',
                    '/img/mascot/heads/zalka-head-11.png',
                    '/img/mascot/heads/zalka-head-15.png',
                ],
                thinking: [
                    '/img/mascot/heads/zalka-head-04.png',
                    '/img/mascot/heads/zalka-head-08.png',
                    '/img/mascot/heads/zalka-head-12.png',
                    '/img/mascot/heads/zalka-head-16.png',
                ],
                sad: [
                    '/img/mascot/heads/zalka-head-13.png',
                    '/img/mascot/heads/zalka-head-14.png',
                    '/img/mascot/heads/zalka-head-15.png',
                    '/img/mascot/heads/zalka-head-16.png',
                ],
                celebrate: [
                    '/img/mascot/heads/zalka-head-03.png',
                    '/img/mascot/heads/zalka-head-07.png',
                    '/img/mascot/heads/zalka-head-11.png',
                    '/img/mascot/heads/zalka-head-15.png',
                ],
            };
            if (parsed && Array.isArray(parsed.neutral)) {
                Object.keys(fallback).forEach((k) => {
                    if (!parsed[k]) parsed[k] = fallback[k];
                    if (!Array.isArray(parsed[k])) parsed[k] = [parsed[k]];
                });
                return parsed;
            }
            return {
                ...fallback,
            };
        }

        cacheDom() {
            this.el = {
                backdrop: this.root.querySelector('[data-dz-tour-backdrop]'),
                hole: this.root.querySelector('[data-dz-tour-hole]'),
                bubbleFloat: this.root.querySelector('[data-dz-tour-stage]'),
                stage: this.root.querySelector('[data-dz-tour-stage]'),
                bubble: this.root.querySelector('[data-dz-tour-bubble]'),
                text: this.root.querySelector('[data-dz-tour-text]'),
                quiz: this.root.querySelector('[data-dz-tour-quiz]'),
                next: this.root.querySelector('[data-dz-tour-next]'),
                mascot: this.root.querySelector('[data-dz-tour-mascot]'),
                tapPaw: this.root.querySelector('[data-dz-tour-tap-paw]'),
                stepProgress: this.root.querySelector('[data-dz-tour-step-progress]'),
                xp: this.root.querySelector('[data-dz-tour-xp]'),
                hearts: this.root.querySelector('[data-dz-tour-hearts]'),
                exit: this.root.querySelector('[data-dz-tour-exit]'),
                overlay: this.root.querySelector('[data-dz-tour-overlay]'),
                overlayContent: this.root.querySelector('[data-dz-tour-overlay-content]'),
                confetti: this.root.querySelector('[data-dz-tour-confetti]'),
                celebrationTpl: this.root.querySelector('[data-dz-celebration-template]'),
            };
        }

        defaultState() {
            return {
                xp: 0,
                streak: 1,
                hearts: this.heartsMax,
                completedLessons: [],
                lastVisit: new Date().toISOString().slice(0, 10),
                activeLesson: null,
                activeStep: 0,
            };
        }

        loadState() {
            try {
                const raw = localStorage.getItem(STORAGE_PREFIX + this.userId);
                const base = raw ? JSON.parse(raw) : this.defaultState();
                base.hearts = base.hearts ?? this.heartsMax;
                base.completedLessons = base.completedLessons || [];
                return base;
            } catch {
                return this.defaultState();
            }
        }

        saveState() {
            localStorage.setItem(STORAGE_PREFIX + this.userId, JSON.stringify(this.state));
            global.dispatchEvent(new CustomEvent('dz-onboarding-state', { detail: this.state }));
        }

        clearActive() {
            this.state.activeLesson = null;
            this.state.activeStep = 0;
            this.saveState();
        }

        allLessons() {
            const list = [];
            (this.quest.units || []).forEach((unit) => {
                (unit.lessons || []).forEach((lesson) => {
                    list.push(lesson);
                });
            });
            return list;
        }

        lessonById(id) {
            return this.allLessons().find((l) => l.id === id) || null;
        }

        nextLessonId(afterId) {
            const lessons = this.allLessons();
            const idx = lessons.findIndex((l) => l.id === afterId);
            if (idx < 0 || idx >= lessons.length - 1) return null;
            return lessons[idx + 1].id;
        }

        isLessonUnlocked(lessonId) {
            const lessons = this.allLessons();
            const idx = lessons.findIndex((l) => l.id === lessonId);
            if (idx === 0) return true;
            return this.state.completedLessons.includes(lessons[idx - 1].id);
        }

        isLessonCompleted(lessonId) {
            return this.state.completedLessons.includes(lessonId);
        }

        bindEvents() {
            this.el.next?.addEventListener('click', () => this.onNext());
            this.el.exit?.addEventListener('click', () => {
                if (confirm(this.labels.exitTour)) {
                    this.endTour();
                }
            });
            this.el.backdrop?.addEventListener('click', (e) => {
                if (e.target === this.el.backdrop) e.preventDefault();
            });
        }

        startLesson(lessonId) {
            const lesson = this.lessonById(lessonId);
            if (!lesson || !this.isLessonUnlocked(lessonId)) return;
            this.currentLesson = lesson;
            this.currentStepIndex = 0;
            this.state.activeLesson = lessonId;
            this.state.activeStep = 0;
            this.saveState();
            this.runStep();
        }

        endTour() {
            this.clearActive();
            this.currentLesson = null;
            this.hideTour();
            this.unhighlight();
        }

        showTour() {
            this.root.classList.remove('d-none');
            this.root.classList.add('dz-tour-active');
            this.mountStagePortal();
            this.bindStageResizeObserver();
            this.renderHud();
        }

        hideTour() {
            this.root.classList.add('d-none');
            this.root.classList.remove('dz-tour-active');
            this.unmountStagePortal();
            this.cleanupClick();
            if (this._stageResizeObs) {
                this._stageResizeObs.disconnect();
                this._stageResizeObs = null;
            }
            if (this.resizeHandler) {
                window.removeEventListener('resize', this.resizeHandler);
                window.removeEventListener('scroll', this.resizeHandler, true);
                this.resizeHandler = null;
            }
        }

        mountStagePortal() {
            const stage = this.el.stage;
            if (!stage) return;
            if (!this.stageHome) {
                this.stageHome = stage.parentNode;
            }
            if (stage.parentNode !== document.body) {
                document.body.appendChild(stage);
            }
            stage.classList.add('dz-tour-stage-portal');
        }

        unmountStagePortal() {
            const stage = this.el.stage;
            if (!stage || !this.stageHome) return;
            stage.classList.remove('dz-tour-stage-portal', 'dz-tour-stage-click-mode');
            if (stage.parentNode !== this.stageHome) {
                this.stageHome.appendChild(stage);
            }
        }

        cleanupClick() {
            (this.clickHandlers || []).forEach(({ el, fn, capture }) => {
                el?.removeEventListener('click', fn, capture);
            });
            this.clickHandlers = [];
            this.root?.classList.remove('dz-tour-click-mode');
            this.el.stage?.classList.remove('dz-tour-stage-click-mode');
            this.el.hole?.classList.remove('dz-tour-hole-click');
            this.hideTapPaw();
        }

        unhighlight() {
            document.querySelectorAll('[data-dz-tour-highlight]').forEach((n) => {
                n.removeAttribute('data-dz-tour-highlight');
                n.classList.remove('dz-tour-click-target');
            });
        }

        handleClickAdvance(target) {
            const steps = this.currentLesson?.steps || [];
            const isLastStep = this.currentStepIndex >= steps.length - 1;

            if (isLastStep) {
                const lessonId = this.currentLesson?.id;
                if (lessonId && !this.state.completedLessons.includes(lessonId)) {
                    this.state.completedLessons.push(lessonId);
                    this.state.xp += this.quest.xpPerLesson || 15;
                    this.state.hearts = Math.min(this.heartsMax, this.state.hearts + 1);
                }
                const nextId = this.nextLessonId(lessonId);
                if (nextId) {
                    this.state.activeLesson = nextId;
                    this.state.activeStep = 0;
                    this.currentLesson = this.lessonById(nextId);
                    this.currentStepIndex = 0;
                } else {
                    this.clearActive();
                }
            } else {
                this.currentStepIndex++;
                this.state.activeStep = this.currentStepIndex;
            }
            this.saveState();
            const href = target?.href || (target?.tagName === 'A' ? target.getAttribute('href') : null);
            if (target?.tagName === 'A' && href) {
                window.location.assign(href);
            } else {
                this.runStep();
            }
        }

        bindClickAdvance(el, target) {
            if (!el) return;
            const fn = (e) => {
                e.preventDefault();
                e.stopPropagation();
                this.cleanupClick();
                this.handleClickAdvance(target || el);
            };
            el.addEventListener('click', fn, true);
            this.clickHandlers.push({ el, fn, capture: true });
        }

        setupClickAdvance(target) {
            if (!target) return;
            this.root?.classList.add('dz-tour-click-mode');
            this.el.stage?.classList.add('dz-tour-stage-click-mode');
            target.classList.add('dz-tour-click-target');
            this.bindClickAdvance(target, target);
            if (this.el.hole) {
                this.el.hole.classList.add('dz-tour-hole-click');
                this.bindClickAdvance(this.el.hole, target);
            }
            this.showTapPaw(target);
        }

        runStep() {
            const steps = this.currentLesson?.steps || [];
            const step = steps[this.currentStepIndex];
            if (!step) {
                this.completeLesson();
                return;
            }

            const needSection = step.section || currentSection();
            if (needSection && needSection !== currentSection()) {
                this.state.activeStep = this.currentStepIndex;
                this.saveState();
                window.location.assign(sectionUrl(needSection));
                return;
            }

            this.showTour();
            requestAnimationFrame(() => this.renderStep(step));
        }

        renderStep(step) {
            const steps = this.currentLesson?.steps || [];
            if (this.el.stepProgress) {
                this.el.stepProgress.style.width = ((this.currentStepIndex + 1) / steps.length) * 100 + '%';
            }

            this.cleanupClick();
            this.unhighlight();
            this.el.quiz?.classList.add('d-none');
            if (this.el.quiz) this.el.quiz.innerHTML = '';

            const mood = step.mood || 'neutral';
            this.setMood(mood);

            const target = this.resolveTarget(step);
            if (target) {
                target.setAttribute('data-dz-tour-highlight', '1');
                this.positionSpotlight(target);
            } else {
                this.centerSpotlight();
            }

            const isQuiz = step.type === 'quiz';
            const isClick = step.type === 'tour_click';

            if (isQuiz) {
                if (this.el.text) this.el.text.textContent = step.question || '';
                this.el.next?.classList.add('d-none');
                this.renderQuiz(step);
            } else {
                if (this.el.text) {
                    this.el.text.textContent = step.text || step.title || '';
                }
                if (isClick) {
                    this.el.next?.classList.add('d-none');
                    this.el.hole?.classList.add('dz-tour-hole-click');
                    if (target) {
                        this.setupClickAdvance(target);
                    } else {
                        this.hideTapPaw();
                    }
                } else if (step.type === 'checkpoint') {
                    this.hideTapPaw();
                    if (this.el.next) this.el.next.textContent = this.labels.continuePath;
                    this.el.next?.classList.remove('d-none');
                } else {
                    this.hideTapPaw();
                    if (this.el.next) this.el.next.textContent = this.labels.continue;
                    this.el.next?.classList.remove('d-none');
                }
            }

            if (!isQuiz) {
                this.positionStage(target);
            }
            this.scheduleStageLayout(target);
            if (isQuiz) {
                setTimeout(() => this.scheduleStageLayout(target), 0);
            }
            this.renderHud();

            if (!this.resizeHandler) {
                this.resizeHandler = () => {
                    const s = this.currentLesson?.steps?.[this.currentStepIndex];
                    if (!s) return;
                    const t = this.resolveTarget(s);
                    if (t) {
                        this.positionSpotlight(t);
                    }
                    if (s.type === 'tour_click' && t) {
                        this.showTapPaw(t);
                    } else if (s.type === 'tour_click') {
                        this.hideTapPaw();
                    }
                    this.positionStage(t || null);
                };
                window.addEventListener('resize', this.resizeHandler);
                window.addEventListener('scroll', this.resizeHandler, true);
            }
            const img = this.el.mascot?.querySelector('[data-dz-mascot-img]');
            if (img && !img.dataset.dzBound) {
                img.dataset.dzBound = '1';
                img.addEventListener('load', () => {
                    const s = this.currentLesson?.steps?.[this.currentStepIndex];
                    if (!s) return;
                    this.scheduleStageLayout(this.resolveTarget(s));
                });
            }
        }

        resolveTarget(step) {
            const sel = step.target;
            if (!sel) return null;
            try {
                return document.querySelector(sel);
            } catch {
                return null;
            }
        }

        positionSpotlight(el) {
            const pad = 8;
            const r = el.getBoundingClientRect();
            const hole = this.el.hole;
            if (!hole) return;
            hole.style.top = (r.top - pad) + 'px';
            hole.style.left = (r.left - pad) + 'px';
            hole.style.width = (r.width + pad * 2) + 'px';
            hole.style.height = (r.height + pad * 2) + 'px';
            hole.classList.remove('dz-tour-hole-click');
        }

        centerSpotlight() {
            const hole = this.el.hole;
            if (!hole) return;
            const w = Math.min(400, window.innerWidth * 0.8);
            const h = 120;
            hole.style.top = (window.innerHeight / 2 - h / 2) + 'px';
            hole.style.left = (window.innerWidth / 2 - w / 2) + 'px';
            hole.style.width = w + 'px';
            hole.style.height = h + 'px';
        }

        topbarInset() {
            const bar = this.root?.querySelector('.dz-tour-topbar');
            return bar ? bar.getBoundingClientRect().height + 12 : 68;
        }

        anchorRect(target) {
            const hole = this.el.hole;
            if (hole) {
                const hr = hole.getBoundingClientRect();
                if (hr.width > 0 && hr.height > 0) {
                    return hr;
                }
            }
            return target ? target.getBoundingClientRect() : null;
        }

        sidebarRightEdge() {
            const sidebar = document.querySelector('.sidebar');
            if (sidebar) {
                return sidebar.getBoundingClientRect().right;
            }
            return Math.min(280, window.innerWidth * 0.22);
        }

        clamp(n, min, max) {
            return Math.max(min, Math.min(max, n));
        }

        measureStage() {
            const stage = this.el.stage;
            if (!stage) return { w: 320, h: 380 };
            const w = stage.offsetWidth || stage.getBoundingClientRect().width || 320;
            const h = stage.offsetHeight || stage.getBoundingClientRect().height || 380;
            return {
                w: Math.min(340, w || 320),
                h: h || 380,
            };
        }

        fitStageInViewport(preferredTop) {
            const stage = this.el.stage;
            if (!stage) return preferredTop;
            const bottomSafe = this.topbarInset();
            const topSafe = 12;
            const stageH = stage.offsetHeight || this.measureStage().h;
            const maxTop = window.innerHeight - bottomSafe - stageH - 8;
            return this.clamp(preferredTop, topSafe, Math.max(topSafe, maxTop));
        }

        ensureMascotVisible() {
            const stage = this.el.stage;
            const mascot = this.el.mascot;
            if (!stage || !mascot) return;
            const bottomSafe = this.topbarInset();
            const topSafe = 12;
            const maxBottom = window.innerHeight - bottomSafe - 8;
            const mr = mascot.getBoundingClientRect();
            if (mr.height < 4 && mr.width < 4) return;
            let top = parseFloat(stage.style.top);
            if (Number.isNaN(top)) top = stage.getBoundingClientRect().top;
            let changed = false;
            if (mr.bottom > maxBottom) {
                top -= mr.bottom - maxBottom;
                changed = true;
            }
            const check = changed ? mascot.getBoundingClientRect() : mr;
            if (check.top < topSafe) {
                top += topSafe - check.top;
                changed = true;
            }
            if (changed) {
                stage.style.top = Math.max(topSafe, top) + 'px';
            }
        }

        bindStageResizeObserver() {
            const stage = this.el.stage;
            if (!stage || this._stageResizeObs || typeof ResizeObserver === 'undefined') return;
            let raf = 0;
            this._stageResizeObs = new ResizeObserver(() => {
                cancelAnimationFrame(raf);
                raf = requestAnimationFrame(() => {
                    if (this.root.classList.contains('d-none')) return;
                    const s = this.currentLesson?.steps?.[this.currentStepIndex];
                    if (!s) return;
                    const top = parseFloat(stage.style.top);
                    if (!Number.isNaN(top)) {
                        stage.style.top = this.fitStageInViewport(top) + 'px';
                    }
                    this.ensureMascotVisible();
                    this.alignBubbleTail(this.resolveTarget(s), this._lastTailPlacement);
                });
            });
            this._stageResizeObs.observe(stage);
        }

        measureBubbleTailY() {
            const stage = this.el.stage;
            const bubble = this.el.bubble;
            if (!stage || !bubble) return 65;
            const stageRect = stage.getBoundingClientRect();
            const bubbleRect = bubble.getBoundingClientRect();
            if (stageRect.height > 0 && bubbleRect.height > 0) {
                return (bubbleRect.top - stageRect.top) + bubbleRect.height / 2;
            }
            return (bubble.offsetHeight || 130) / 2;
        }

        refineStageVerticalAlign(target, tailPlacement) {
            if (!target || !this.el.stage) return;
            if (tailPlacement !== 'left' && tailPlacement !== 'right') return;
            const r = this.anchorRect(target);
            if (!r) return;
            const stage = this.el.stage;
            const tailY = this.measureBubbleTailY();
            const targetCy = r.top + r.height / 2;
            const top = this.fitStageInViewport(targetCy - tailY);
            stage.style.top = top + 'px';
        }

        scheduleStageLayout(target) {
            const run = () => {
                this.positionStage(target);
                this.refineStageVerticalAlign(target, this._lastTailPlacement);
                const stage = this.el.stage;
                if (stage) {
                    const top = parseFloat(stage.style.top);
                    if (!Number.isNaN(top)) {
                        stage.style.top = this.fitStageInViewport(top) + 'px';
                    }
                }
                this.ensureMascotVisible();
            };
            requestAnimationFrame(() => {
                run();
                requestAnimationFrame(run);
            });
        }

        alignBubbleTail(target, placement) {
            const bubble = this.el.bubble;
            const tail = bubble?.querySelector('.dz-comic-tail');
            if (!bubble || !tail) return;

            const tailSide = placement || 'top';
            bubble.dataset.dzTail = tailSide;
            tail.style.marginLeft = '';
            tail.style.marginTop = '';
            tail.style.top = '';
            tail.style.left = '';

            if (!target) return;

            const r = this.anchorRect(target);
            if (!r) return;
            const b = bubble.getBoundingClientRect();

            if (tailSide === 'top' || tailSide === 'bottom') {
                const targetCx = r.left + r.width / 2;
                const offset = targetCx - (b.left + b.width / 2);
                const clamped = Math.max(-b.width / 2 + 28, Math.min(b.width / 2 - 28, offset));
                tail.style.marginLeft = (b.width / 2 + clamped) + 'px';
            }
        }

        positionStage(target) {
            const stage = this.el.stage;
            if (!stage) return;
            this.mountStagePortal();

            const margin = 16;
            const bottomSafe = this.topbarInset();
            const topSafe = 12;
            const stageW = Math.min(340, stage.offsetWidth || stage.getBoundingClientRect().width || 320);
            stage.style.width = stageW + 'px';
            const stageH = stage.offsetHeight || 380;
            let top;
            let left;
            let tailPlacement = 'left';

            const r = this.anchorRect(target);
            if (r) {
                const sidebarEdge = this.sidebarRightEdge();
                const inSidebar = r.right <= sidebarEdge + 4;
                const onRight = r.left >= window.innerWidth * 0.62;

                if (inSidebar) {
                    left = r.right + margin;
                    top = r.top + r.height / 2 - this.measureBubbleTailY();
                    tailPlacement = 'left';
                } else if (onRight) {
                    left = r.left - stageW - margin;
                    top = r.top + r.height / 2 - this.measureBubbleTailY();
                    tailPlacement = 'right';
                } else {
                    const liveH = stage.offsetHeight || stageH;
                    const spaceBelow = window.innerHeight - bottomSafe - r.bottom - margin;
                    const spaceAbove = r.top - margin - topSafe;
                    if (spaceBelow >= liveH) {
                        top = r.bottom + margin;
                        tailPlacement = 'top';
                    } else if (spaceAbove >= liveH) {
                        top = r.top - liveH - margin;
                        tailPlacement = 'bottom';
                    } else if (spaceBelow >= spaceAbove) {
                        top = r.bottom + margin;
                        tailPlacement = 'top';
                    } else {
                        top = r.top - liveH - margin;
                        tailPlacement = 'bottom';
                    }
                    left = r.left + r.width / 2 - stageW / 2;
                }

                top = this.fitStageInViewport(top);
                left = this.clamp(left, margin, window.innerWidth - stageW - margin);
            } else {
                top = window.innerHeight - bottomSafe - stageH - margin;
                left = window.innerWidth - stageW - margin - 20;
                tailPlacement = 'top';
                top = this.fitStageInViewport(top);
            }

            stage.style.top = top + 'px';
            stage.style.left = left + 'px';
            stage.style.right = 'auto';
            stage.style.bottom = 'auto';
            stage.style.transform = 'none';
            this._lastTailPlacement = tailPlacement;
            this.ensureMascotVisible();
            requestAnimationFrame(() => {
                this.refineStageVerticalAlign(target, tailPlacement);
                this.alignBubbleTail(target, tailPlacement);
                this.ensureMascotVisible();
            });
        }

        renderQuiz(step) {
            if (!this.el.quiz) return;
            this.el.quiz.classList.remove('d-none');
            (step.options || []).forEach((opt, i) => {
                const btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'dz-quiz-option';
                btn.textContent = opt;
                btn.addEventListener('click', () => this.answerQuiz(step, i, btn));
                this.el.quiz.appendChild(btn);
            });
            const t = this.resolveTarget(step);
            requestAnimationFrame(() => this.scheduleStageLayout(t));
        }

        answerQuiz(step, index, btn) {
            const correct = step.correct === index;
            this.el.quiz?.querySelectorAll('.dz-quiz-option').forEach((b) => {
                b.disabled = true;
            });
            if (correct) {
                btn.classList.add('dz-quiz-correct');
                this.setMood('happy');
                if (this.el.text) this.el.text.textContent = step.explain || this.labels.excellent;
                this.scheduleStageLayout(this.resolveTarget(step));
                setTimeout(() => this.advance(), 1100);
            } else {
                btn.classList.add('dz-quiz-wrong');
                this.state.hearts = Math.max(0, this.state.hearts - 1);
                this.saveState();
                this.setMood('sad');
                if (this.el.text) {
                    let msg = step.explain || this.labels.tryAgain;
                    if (this.state.hearts <= 0) msg += this.labels.heartsOut;
                    this.el.text.textContent = msg;
                }
                this.scheduleStageLayout(this.resolveTarget(step));
                if (this.state.hearts <= 0) {
                    this.state.hearts = this.heartsMax;
                    this.saveState();
                }
                setTimeout(() => {
                    this.el.quiz?.querySelectorAll('.dz-quiz-option').forEach((b) => {
                        b.disabled = false;
                        b.classList.remove('dz-quiz-wrong', 'dz-quiz-correct');
                    });
                    const s = this.currentLesson?.steps?.[this.currentStepIndex];
                    if (s) this.renderStep(s);
                }, 1600);
            }
            this.renderHud();
        }

        onNext() {
            const step = this.currentLesson?.steps?.[this.currentStepIndex];
            if (step?.type === 'checkpoint') {
                this.endTour();
                const onboarding = sectionUrl('onboarding');
                if (currentSection() !== 'onboarding') {
                    window.location.assign(onboarding);
                }
                return;
            }
            this.advance();
        }

        advance() {
            this.currentStepIndex++;
            this.state.activeStep = this.currentStepIndex;
            this.saveState();
            const steps = this.currentLesson?.steps || [];
            if (this.currentStepIndex >= steps.length) {
                this.completeLesson();
            } else {
                this.runStep();
            }
        }

        completeLesson() {
            const id = this.currentLesson?.id;
            if (id && !this.state.completedLessons.includes(id)) {
                this.state.completedLessons.push(id);
                const xpGain = this.quest.xpPerLesson || 15;
                this.state.xp += xpGain;
                this.state.hearts = Math.min(this.heartsMax, this.state.hearts + 1);
            }
            this.clearActive();
            this.showCelebration(this.quest.xpPerLesson || 15);
        }

        showCelebration(xpGain) {
            this.setMood('celebrate');
            this.el.confetti?.classList.remove('d-none');
            this.fireConfetti();
            if (!this.el.overlay || !this.el.overlayContent) return;
            const tpl = this.el.celebrationTpl;
            if (tpl) {
                const clone = tpl.content.cloneNode(true);
                const xpEl = clone.querySelector('[data-dz-celebration-xp]');
                const textEl = clone.querySelector('[data-dz-celebration-text]');
                if (xpEl) xpEl.textContent = '+' + xpGain + ' XP';
                if (textEl) {
                    textEl.textContent = (this.quest.mascot?.name || this.labels.mascot) + ' ' + this.labels.proud;
                }
                this.el.overlayContent.innerHTML = '';
                this.el.overlayContent.appendChild(clone);
            }
            this.el.overlay.classList.remove('d-none');
            this.el.overlayContent.querySelector('[data-dz-celebration-close]')?.addEventListener('click', () => {
                this.el.overlay.classList.add('d-none');
                this.el.confetti?.classList.add('d-none');
                const next = this.nextLessonId(this.currentLesson?.id);
                this.hideTour();
                if (next && this.isLessonUnlocked(next)) {
                    this.startLesson(next);
                } else {
                    this.endTour();
                }
            });
        }

        fireConfetti() {
            const canvas = this.el.confetti;
            if (!canvas) return;
            const ctx = canvas.getContext('2d');
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
            const colors = ['#ff5b2e', '#ffcb45', '#14b88a', '#3b9eff'];
            const pieces = Array.from({ length: 70 }, () => ({
                x: Math.random() * canvas.width,
                y: -20,
                r: 4 + Math.random() * 5,
                c: colors[Math.floor(Math.random() * colors.length)],
                vy: 2 + Math.random() * 4,
                vx: -2 + Math.random() * 4,
                rot: Math.random() * 360,
                vr: -6 + Math.random() * 12,
            }));
            let frame = 0;
            const draw = () => {
                ctx.clearRect(0, 0, canvas.width, canvas.height);
                pieces.forEach((p) => {
                    p.x += p.vx;
                    p.y += p.vy;
                    p.vy += 0.1;
                    p.rot += p.vr;
                    ctx.save();
                    ctx.translate(p.x, p.y);
                    ctx.rotate((p.rot * Math.PI) / 180);
                    ctx.fillStyle = p.c;
                    ctx.fillRect(-p.r / 2, -p.r / 2, p.r, p.r);
                    ctx.restore();
                });
                if (++frame < 80) requestAnimationFrame(draw);
                else ctx.clearRect(0, 0, canvas.width, canvas.height);
            };
            draw();
        }

        setMood(mood) {
            const moods = ['neutral', 'happy', 'excited', 'thinking', 'sad', 'celebrate'];
            const m = moods.includes(mood) ? mood : 'neutral';
            const faceKey = {
                neutral: 'neutral',
                happy: 'happy',
                excited: 'excited',
                thinking: 'thinking',
                sad: 'sad',
                celebrate: 'celebrate',
            }[m] || 'neutral';
            const img = this.el.mascot?.querySelector('[data-dz-mascot-img]');
            if (img) {
                const variants = Array.isArray(this.mascotFaces[faceKey]) ? this.mascotFaces[faceKey] : [this.mascotFaces[faceKey]];
                const neutralVariants = Array.isArray(this.mascotFaces.neutral) ? this.mascotFaces.neutral : [this.mascotFaces.neutral];
                const pool = variants.filter(Boolean).length ? variants.filter(Boolean) : neutralVariants.filter(Boolean);
                if (!this._moodCursor) this._moodCursor = {};
                const i = this._moodCursor[faceKey] || 0;
                const base = pool[i % pool.length] || '';
                this._moodCursor[faceKey] = i + 1;
                const sep = base.includes('?') ? '&' : '?';
                img.src = base + sep + 'm=' + faceKey + '-' + i;
            }
            this.el.mascot?.querySelector('.dz-mascot-wrap')?.classList.toggle('dz-mascot-bounce', m === 'celebrate' || m === 'excited');
            this.el.mascot?.querySelector('.dz-mascot-wrap')?.classList.toggle('dz-mascot-wiggle', m === 'sad');
        }

        showTapPaw(target) {
            const paw = this.el.tapPaw;
            if (!paw || !target) return;
            const r = target.getBoundingClientRect();
            if (!r) return;
            if (r.width <= 0 || r.height <= 0) {
                this.hideTapPaw();
                return;
            }
            const placeRight = r.left < window.innerWidth * 0.5;
            const pawBase = '/img/mascot/trainer-paw.png';
            paw.src = pawBase + '?v=3';
            paw.classList.toggle('dz-tour-tap-paw-flip', !placeRight);
            const x = r.left + r.width * 0.82;
            const y = r.top + r.height * 0.5;
            paw.style.left = x + 'px';
            paw.style.top = y + 'px';
            paw.classList.remove('d-none');
        }

        hideTapPaw() {
            const paw = this.el.tapPaw;
            if (!paw) return;
            paw.classList.add('d-none');
            paw.removeAttribute('src');
        }

        renderHud() {
            if (this.el.xp) this.el.xp.textContent = (this.state.xp || 0) + ' XP';
            if (this.el.hearts) {
                this.el.hearts.innerHTML = '';
                for (let i = 0; i < this.heartsMax; i++) {
                    const span = document.createElement('span');
                    span.className = 'dz-heart ' + (i < this.state.hearts ? 'dz-heart-full' : 'dz-heart-empty');
                    this.el.hearts.appendChild(span);
                }
            }
        }
    }

    class DobrozalMap {
        constructor(root, quest, tour) {
            this.root = root;
            this.quest = quest;
            this.tour = tour;
            this.userId = root.dataset.userId || 'guest';
            this.labels = readLabels(root);
            this.state = tour.state;
            this.el = {
                progressFill: root.querySelector('[data-dz-progress-fill]'),
                progressText: root.querySelector('[data-dz-progress-text]'),
                xp: root.querySelector('[data-dz-xp]'),
                streak: root.querySelector('[data-dz-streak]'),
                hearts: root.querySelector('[data-dz-hearts]'),
                path: root.querySelector('[data-dz-path]'),
            };
            this.bindEvents();
            this.render();
            global.addEventListener('dz-onboarding-state', () => {
                this.state = tour.state;
                this.render();
            });
        }

        allLessons() {
            return this.tour.allLessons();
        }

        bindEvents() {
            this.el.path?.addEventListener('click', (e) => {
                const row = e.target.closest('[data-lesson-id]');
                if (!row || row.classList.contains('dz-lesson-locked')) return;
                const id = row.getAttribute('data-lesson-id');
                if (id) this.tour.startLesson(id);
            });
            this.root.querySelector('[data-dz-reset]')?.addEventListener('click', () => {
                if (confirm(this.labels.resetConfirm)) {
                    this.tour.state = this.tour.defaultState();
                    this.tour.saveState();
                    this.render();
                }
            });
        }

        render() {
            const total = this.quest.totalLessons || this.allLessons().length;
            const done = this.state.completedLessons.length;
            if (this.el.progressFill) {
                this.el.progressFill.style.width = (total ? Math.round((done / total) * 100) : 0) + '%';
            }
            if (this.el.progressText) this.el.progressText.textContent = done + ' / ' + total;
            if (this.el.xp) this.el.xp.textContent = String(this.state.xp || 0);
            if (this.el.streak) this.el.streak.textContent = String(this.state.streak || 1);
            if (this.el.hearts) {
                this.el.hearts.innerHTML = '';
                const max = this.quest.heartsMax || 5;
                for (let i = 0; i < max; i++) {
                    const span = document.createElement('span');
                    span.className = 'dz-heart ' + (i < (this.state.hearts ?? max) ? 'dz-heart-full' : 'dz-heart-empty');
                    this.el.hearts.appendChild(span);
                }
            }
            this.el.path?.querySelectorAll('[data-lesson-id]').forEach((node) => {
                const id = node.getAttribute('data-lesson-id');
                const doneL = this.state.completedLessons.includes(id);
                const unlocked = this.tour.isLessonUnlocked(id);
                const lessons = this.allLessons();
                const next = lessons.find((l) => !this.state.completedLessons.includes(l.id) && this.tour.isLessonUnlocked(l.id));
                const current = next?.id === id;
                node.classList.toggle('dz-lesson-done', doneL);
                node.classList.toggle('dz-lesson-locked', !unlocked && !doneL);
                node.classList.toggle('dz-lesson-current', current && !doneL);
                node.disabled = !unlocked;
                const num = node.querySelector('.dz-lesson-row-num');
                if (num && doneL) num.textContent = '\u2713';
                const action = node.querySelector('.dz-lesson-row-action');
                if (action) {
                    action.textContent = doneL ? this.labels.lessonDoneMark : unlocked ? this.labels.lessonStart : '\uD83D\uDD12';
                }
            });
        }
    }

    function boot() {
        const quest = parseQuest();
        if (!quest?.units?.length) return;

        const tourRoot = document.getElementById('dz-tour-root');
        const userId = tourRoot?.dataset.userId || 'guest';
        let tour = null;
        if (tourRoot) {
            tour = new DobrozalTour(tourRoot, quest, userId);
            global.dobrozalTour = tour;
        }

        const mapRoot = document.getElementById('dz-onboarding-root');
        if (mapRoot && tour) {
            new DobrozalMap(mapRoot, quest, tour);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})(typeof window !== 'undefined' ? window : this);
