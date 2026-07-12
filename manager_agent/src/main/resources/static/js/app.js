/* ============================================================
   AiTripPlan — AI 旅行规划助手 交互脚本
   ============================================================ */

(function () {
    'use strict';

    // ========================
    // DOM References
    // ========================
    const $ = (sel) => document.querySelector(sel);
    const $$ = (sel) => document.querySelectorAll(sel);

    const promptInput = $('#promptInput');
    const sendBtn = $('#sendBtn');
    const sendBtnIcon = $('#sendBtnIcon');
    const messagesContainer = $('#messagesContainer');
    const welcomeScreen = $('#welcomeScreen');
    const thinkingIndicator = $('#thinkingIndicator');
    const toastContainer = $('#toastContainer');
    const buildPromptBtn = $('#buildPromptBtn');
    const charCount = $('#charCount');
    const statusIndicator = $('#statusIndicator');
    const statusText = $('#statusText');
    const destinationInput = $('#destination');
    const departureInput = $('#departure');
    const travelModeSelect = $('#travelMode');
    const departureDateInput = $('#departureDate');
    const daysSelect = $('#days');
    const budgetSelect = $('#budget');
    const preferenceTags = $$('#preferenceTags .tag');

    // ========================
    // State
    // ========================
    let isSending = false;
    let messageCount = 0;
    let currentAbortController = null;

    // ========================
    // Markdown Renderer
    // ========================
    const md = window.markdownit({
        html: false,
        breaks: true,
        linkify: true,
        typographer: true,
    });

    function renderMarkdown(text) {
        if (!text) return '';
        return md.render(text);
    }

    // ========================
    // Toast Notifications
    // ========================
    function showToast(message, type) {
        type = type || 'error';
        const toast = document.createElement('div');
        toast.className = 'toast ' + type;
        const icons = { error: 'ri-error-warning-line', success: 'ri-check-line', warning: 'ri-alert-line' };
        toast.innerHTML = '<i class="' + (icons[type] || icons.error) + '"></i> ' + escapeHtml(message);
        toastContainer.appendChild(toast);

        setTimeout(function () {
            toast.classList.add('removing');
            setTimeout(function () {
                if (toast.parentNode) toast.parentNode.removeChild(toast);
            }, 300);
        }, 4000);
    }

    // ========================
    // Escape HTML (for non-markdown user text)
    // ========================
    function escapeHtml(str) {
        var div = document.createElement('div');
        div.appendChild(document.createTextNode(str));
        return div.innerHTML;
    }

    // ========================
    // Stop Generation
    // ========================
    function stopGeneration() {
        if (currentAbortController) {
            currentAbortController.abort();
            currentAbortController = null;
        }
        resetSendState();
        thinkingIndicator.classList.add('hidden');
        addMessage('assistant', '⏹️ 已停止生成。');
    }

    function resetSendState() {
        if (!isSending) return;
        isSending = false;
        currentAbortController = null;
        sendBtnIcon.className = 'ri-send-plane-fill';
        sendBtn.title = '发送';
        sendBtn.classList.remove('sending');
        promptInput.disabled = false;
        thinkingIndicator.classList.add('hidden');
        // defer focus to avoid layout thrashing with large text
        setTimeout(function () { promptInput.focus(); }, 300);
    }

    // ========================
    // API: Check backend health
    // ========================
    function checkHealth() {
        fetch('/trip', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json;charset=UTF-8' },
            body: JSON.stringify({ prompt: '__ping__' }),
        })
        .then(function (resp) {
            // We just care that the endpoint responds (even if it errors on the prompt)
            setStatus(true);
        })
        .catch(function () {
            setStatus(false);
        });
    }

    function setStatus(online) {
        statusIndicator.className = 'status-dot ' + (online ? 'status-online' : 'status-offline');
        statusText.textContent = online ? '服务在线' : '服务离线';
    }

    // ========================
    // API: Send trip request (streaming)
    // ========================
    function sendPrompt(promptText) {
        if (isSending) {
            stopGeneration();
            return;
        }
        if (!promptText || !promptText.trim()) return;

        promptText = promptText.trim();
        isSending = true;
        sendBtnIcon.className = 'ri-stop-circle-fill';
        sendBtn.title = '停止生成';
        sendBtn.classList.add('sending');
        promptInput.disabled = true;
        scrollToBottom();

        // Add user message
        addMessage('user', promptText);
        promptInput.value = '';
        autoResize(promptInput);
        updateCharCount();

        // ---- Streaming state ----
        var aiMsgDiv = null;
        var aiBubble = null;
        var fullResponseText = '';
        var thinkingDiv = null;
        var streamDone = false;
        var domUpdateTimer = null;

        currentAbortController = new AbortController();

        fetch('/trip/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json;charset=UTF-8' },
            body: JSON.stringify({ prompt: promptText }),
            signal: currentAbortController.signal,
        })
        .then(function (resp) {
            if (!resp.ok) throw new Error('服务器返回错误: HTTP ' + resp.status);
            var reader = resp.body.getReader();
            var decoder = new TextDecoder();
            var buffer = '';

            function readStream() {
                if (streamDone) { reader.cancel(); return; }
                return reader.read().then(function (result) {
                    if (result.done || streamDone) { reader.cancel(); return; }
                    buffer += decoder.decode(result.value, { stream: true });
                    var lines = buffer.split('\n');
                    buffer = lines.pop() || '';
                    for (var i = 0; i < lines.length; i++) {
                        var line = lines[i].trim();
                        if (line.startsWith('data:')) {
                            var jsonStr = line.substring(5).trim();
                            try { handleStreamEvent(JSON.parse(jsonStr)); } catch (e) {}
                        }
                    }
                    return readStream();
                });
            }
            return readStream();
        })
        .catch(function (err) {
            clearTimeout(domUpdateTimer);
            if (err.name === 'AbortError') { /* handled in stopGeneration */ return; }
            console.error('Stream error:', err);
            if (aiMsgDiv) aiMsgDiv.remove();
            if (thinkingDiv) thinkingDiv.remove();
            addMessage('assistant', '❌ 请求失败：' + escapeHtml(err.message));
            showToast('请求失败: ' + err.message, 'error');
            setStatus(false);
            resetSendState();
        });

        // ---- Handle each SSE event ----
        function handleStreamEvent(event) {
            var etype = event.type;
            var text = event.text || '';

            if (etype === 'DONE') {
                streamDone = true;
                clearTimeout(domUpdateTimer);
                if (aiBubble && fullResponseText) {
                    aiBubble.textContent = fullResponseText;
                }
                if (thinkingDiv) thinkingDiv.remove();
                // Add download button
                if (aiMsgDiv && fullResponseText) {
                    addDownloadButton(aiMsgDiv, fullResponseText);
                }
                scrollToBottom();
                resetSendState();
                return;
            }

            if (etype === 'ERROR') {
                if (thinkingDiv) thinkingDiv.classList.add('hidden');
                fullResponseText += '\n\n⚠️ ' + text;
                scheduleDomUpdate();
                return;
            }

            if (etype === 'REASONING') return;

            if (etype === 'TEXT' || etype === 'TOOL_RESULT') {
                if (thinkingDiv && etype === 'TEXT') thinkingDiv.classList.add('hidden');

                if (!aiMsgDiv) {
                    aiMsgDiv = document.createElement('div');
                    aiMsgDiv.className = 'message assistant';
                    var avatar = document.createElement('div');
                    avatar.className = 'message-avatar';
                    avatar.textContent = '🤖';
                    aiBubble = document.createElement('div');
                    aiBubble.className = 'message-bubble';
                    aiMsgDiv.appendChild(avatar);
                    aiMsgDiv.appendChild(aiBubble);
                    messagesContainer.appendChild(aiMsgDiv);
                    if (messageCount === 0) {
                        welcomeScreen.style.display = 'none';
                        messagesContainer.style.display = 'flex';
                    }
                    messageCount++;
                }

                fullResponseText += text;
                scheduleDomUpdate();
            }
        }

        // Batch DOM updates at most once per animation frame
        function scheduleDomUpdate() {
            if (domUpdateTimer) return;
            domUpdateTimer = setTimeout(function () {
                domUpdateTimer = null;
                if (aiBubble && fullResponseText) {
                    aiBubble.textContent = fullResponseText;
                    scrollToBottom();
                }
            }, 50);
        }

        function createThinkingPanel() {
            var div = document.createElement('div');
            div.className = 'message assistant thinking-panel';
            var avatar = document.createElement('div');
            avatar.className = 'message-avatar';
            avatar.textContent = '🧠';
            var body = document.createElement('div');
            body.className = 'message-bubble thinking-bubble-body';
            body.innerHTML = '<div class="thinking-header"><i class="ri-brain-line"></i> AI 思考中...</div>' +
                           '<div class="thinking-content"></div>';
            div.appendChild(avatar);
            div.appendChild(body);
            messagesContainer.appendChild(div);
            if (messageCount === 0) {
                welcomeScreen.style.display = 'none';
                messagesContainer.style.display = 'flex';
            }
            return div;
        }
    }

    // ========================
    // Download Plan
    // ========================
    function addDownloadButton(msgDiv, planText) {
        var btnDiv = document.createElement('div');
        btnDiv.className = 'download-bar';
        btnDiv.innerHTML = '<button class="download-btn" title="下载行程文件 CSV">' +
            '<i class="ri-download-line"></i> 下载行程文件</button>';
        btnDiv.querySelector('.download-btn').addEventListener('click', function () {
            downloadPlan(planText);
        });
        msgDiv.appendChild(btnDiv);
    }

    function downloadPlan(planText) {
        fetch('/trip/export', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json;charset=UTF-8' },
            body: JSON.stringify({ prompt: planText }),
        })
        .then(function (resp) {
            if (!resp.ok) throw new Error('导出失败');
            return resp.blob();
        })
        .then(function (blob) {
            var url = URL.createObjectURL(blob);
            var a = document.createElement('a');
            a.href = url;
            a.download = 'trip_plan.csv';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
            showToast('行程文件下载成功', 'success');
        })
        .catch(function (err) {
            showToast('下载失败: ' + err.message, 'error');
        });
    }

    // ========================
    // Add Message to Chat
    // ========================
    function addMessage(role, text, isMarkdown) {
        // Hide welcome screen when first message appears
        if (messageCount === 0) {
            welcomeScreen.style.display = 'none';
            messagesContainer.style.display = 'flex';
        }
        messageCount++;

        var msgDiv = document.createElement('div');
        msgDiv.className = 'message ' + role;

        var avatar = document.createElement('div');
        avatar.className = 'message-avatar';

        if (role === 'user') {
            avatar.textContent = '👤';
        } else {
            avatar.textContent = '🤖';
        }

        var bubble = document.createElement('div');
        bubble.className = 'message-bubble';

        if (isMarkdown) {
            bubble.innerHTML = renderMarkdown(text);
        } else {
            bubble.textContent = text;
        }

        msgDiv.appendChild(avatar);
        msgDiv.appendChild(bubble);

        // Append to messages container
        messagesContainer.appendChild(msgDiv);

        scrollToBottom();
    }

    // ========================
    // Build Prompt from Sidebar
    // ========================
    function buildPrompt() {
        var destination = destinationInput.value.trim();
        var departure = departureInput.value.trim();
        var travelMode = travelModeSelect.value;
        var departureDate = departureDateInput.value;
        var days = daysSelect.value;
        var budget = budgetSelect.value;
        var preferences = [];
        preferenceTags.forEach(function (tag) {
            if (tag.classList.contains('active')) {
                preferences.push(tag.getAttribute('data-value'));
            }
        });

        if (!destination && !departure && !departureDate && !days && !preferences.length && !budget) {
            showToast('请至少填写一个旅行参数', 'warning');
            return;
        }

        var parts = ['帮我规划'];
        if (departure) {
            parts.push('一个从' + departure + '出发');
        }
        if (departureDate) {
            parts.push(departureDate);
        }
        if (destination) {
            parts.push('去' + destination);
        }
        if (days) {
            parts.push(days + '天');
        }
        if (travelMode) {
            parts.push('的' + travelMode + '旅行');
        } else {
            parts.push('的旅行');
        }

        if (preferences.length > 0) {
            parts.push('，喜欢' + preferences.join('、'));
        }

        if (budget) {
            parts.push('，' + budget);
        }

        var promptText = parts.join('');

        // Set to input for user to review and edit, don't auto-send
        promptInput.value = promptText;
        updateCharCount();
        autoResize(promptInput);
        promptInput.focus();

    }

    // ========================
    // Textarea Auto-resize
    // ========================
    function autoResize(textarea) {
        textarea.style.height = 'auto';
        textarea.style.height = Math.min(textarea.scrollHeight, 120) + 'px';
    }

    function updateCharCount() {
        var len = promptInput.value.length;
        charCount.textContent = len + ' / 2000';
        if (len > 1800) {
            charCount.style.color = 'var(--color-error)';
        } else {
            charCount.style.color = '';
        }
    }

    // ========================
    // Scroll to Bottom
    // ========================
    function scrollToBottom() {
        requestAnimationFrame(function () {
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        });
    }


    // ========================
    // Event Listeners
    // ========================

    // Send button (toggle: send / stop)
    sendBtn.addEventListener('click', function () {
        if (isSending) {
            stopGeneration();
        } else {
            var text = promptInput.value.trim();
            if (text) sendPrompt(text);
        }
    });

    // Enter to send, Shift+Enter for newline
    promptInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            var text = promptInput.value.trim();
            if (text) sendPrompt(text);
        }
    });

    // Auto-resize on input
    promptInput.addEventListener('input', function () {
        autoResize(promptInput);
        updateCharCount();
    });

    // Build prompt button
    buildPromptBtn.addEventListener('click', buildPrompt);

    // Preference tags toggle
    preferenceTags.forEach(function (tag) {
        tag.addEventListener('click', function () {
            tag.classList.toggle('active');
        });
    });

    // Quick template buttons
    $$('.template-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var promptText = btn.getAttribute('data-prompt');
            promptInput.value = promptText;
            updateCharCount();
            autoResize(promptInput);
            sendPrompt(promptText);
            closeSidebar();
        });
    });

    // Suggestion chips (welcome screen)
    $$('.suggestion-chip').forEach(function (chip) {
        chip.addEventListener('click', function () {
            var promptText = chip.getAttribute('data-prompt');
            promptInput.value = promptText;
            updateCharCount();
            autoResize(promptInput);
            sendPrompt(promptText);
        });
    });


    // ========================
    // Initialize
    // ========================
    function init() {
        // Check backend health
        checkHealth();
        // Re-check every 30 seconds
        setInterval(checkHealth, 30000);

        // Focus input on load
        promptInput.focus();
    }

    // Run on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
