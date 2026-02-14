import { useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Loader2, MessageCircle, Send, X } from 'lucide-react';
import AssistantService from '../services/AssistantService';

const initialMessage = {
  id: 'assistant-welcome',
  role: 'assistant',
  content:
    "Hey, I am UPI-Lite Assistant. Ask me anything about payments, OTP, QR, KYC, contacts, or architecture.",
  fallback: false,
};

const AssistantWidget = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState([initialMessage]);
  const [input, setInput] = useState('');
  const [isThinking, setIsThinking] = useState(false);

  const messagesRef = useRef(null);

  useEffect(() => {
    if (!messagesRef.current) {
      return;
    }
    messagesRef.current.scrollTop = messagesRef.current.scrollHeight;
  }, [messages, isThinking]);

  const canSend = useMemo(
    () => !isThinking && input.trim().length > 0 && input.trim().length <= 500,
    [input, isThinking]
  );

  const handleSend = async () => {
    const userInput = input.trim();
    if (!userInput || isThinking) {
      return;
    }

    const userMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: userInput,
      fallback: false,
    };

    const nextHistory = [...messages, userMessage];
    setMessages(nextHistory);
    setInput('');
    setIsThinking(true);

    try {
      const response = await AssistantService.chatWithAssistant({
        message: userInput,
        history: nextHistory.map((entry) => ({
          role: entry.role,
          content: entry.content,
        })),
      });

      setMessages((previous) => [
        ...previous,
        {
          id: `assistant-${Date.now()}`,
          role: 'assistant',
          content: response?.reply || 'I could not generate a response right now.',
          fallback: Boolean(response?.fallback),
        },
      ]);
    } catch {
      setMessages((previous) => [
        ...previous,
        {
          id: `assistant-error-${Date.now()}`,
          role: 'assistant',
          content:
            'I am facing a temporary issue. Please retry in a moment. You can still use Dashboard -> Send Money for payments.',
          fallback: true,
        },
      ]);
    } finally {
      setIsThinking(false);
    }
  };

  const handleInputKeyDown = async (event) => {
    if (event.key !== 'Enter' || event.shiftKey) {
      return;
    }
    event.preventDefault();
    await handleSend();
  };

  return (
    <div className="pointer-events-none fixed bottom-24 right-4 z-40 md:bottom-5 md:right-5">
      <AnimatePresence>
        {isOpen ? (
          <motion.div
            initial={{ opacity: 0, y: 12, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 10, scale: 0.96 }}
            className="pointer-events-auto mb-3 w-[min(92vw,380px)] rounded-2xl border border-white/20 bg-slate-900/95 shadow-2xl backdrop-blur"
          >
            <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
              <div>
                <h3 className="text-sm font-semibold text-white">UPI-Lite Assistant</h3>
                <p className="text-[11px] text-gray-400">Built-in app guide</p>
              </div>
              <button
                type="button"
                onClick={() => setIsOpen(false)}
                className="rounded-lg p-1 text-gray-300 hover:bg-white/10 hover:text-white"
                aria-label="Close assistant"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div ref={messagesRef} className="max-h-[360px] space-y-3 overflow-y-auto px-4 py-3">
              {messages.map((message) => (
                <div
                  key={message.id}
                  className={`rounded-xl px-3 py-2 text-sm ${
                    message.role === 'user'
                      ? 'ml-8 bg-cyan-500/20 text-cyan-100'
                      : 'mr-8 border border-white/10 bg-white/5 text-gray-100'
                  }`}
                >
                  <p className="whitespace-pre-wrap break-words">{message.content}</p>
                  {message.role === 'assistant' && message.fallback ? (
                    <p className="mt-1 text-[10px] uppercase tracking-wide text-amber-300">Local reply mode</p>
                  ) : null}
                </div>
              ))}

              {isThinking ? (
                <div className="mr-8 flex items-center rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-xs text-gray-300">
                  <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" />
                  Thinking...
                </div>
              ) : null}
            </div>

            <div className="border-t border-white/10 p-3">
              <div className="flex items-end gap-2">
                <textarea
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  onKeyDown={handleInputKeyDown}
                  placeholder="Ask about app features, flow, or architecture..."
                  rows={2}
                  maxLength={500}
                  className="min-h-14 flex-1 resize-none rounded-xl border border-white/20 bg-white/10 px-3 py-2 text-sm text-white placeholder-gray-400 outline-none focus:border-cyan-400/50 focus:ring-2 focus:ring-cyan-500/30"
                />
                <button
                  type="button"
                  disabled={!canSend}
                  onClick={() => {
                    void handleSend();
                  }}
                  className="rounded-xl bg-gradient-to-r from-cyan-500 to-sky-500 p-2.5 text-white disabled:cursor-not-allowed disabled:opacity-50"
                  aria-label="Send message"
                >
                  <Send className="h-4 w-4" />
                </button>
              </div>
              <p className="mt-1 text-[11px] text-gray-500">Shift+Enter for newline.</p>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>

      <button
        type="button"
        onClick={() => setIsOpen((previous) => !previous)}
        className="pointer-events-auto ml-auto flex items-center gap-2 rounded-full border border-cyan-200/40 bg-gradient-to-r from-cyan-500 to-sky-600 px-4 py-2 text-sm font-semibold text-white shadow-lg shadow-cyan-900/30"
      >
        <MessageCircle className="h-4 w-4" />
        Assistant
      </button>
    </div>
  );
};

export default AssistantWidget;
