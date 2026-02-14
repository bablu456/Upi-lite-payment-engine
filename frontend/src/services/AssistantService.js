import api from './api';

export const chatWithAssistant = async ({ message, history = [] }) => {
  const sanitizedHistory = Array.isArray(history)
    ? history
        .filter(
          (item) =>
            item &&
            (item.role === 'user' || item.role === 'assistant') &&
            typeof item.content === 'string' &&
            item.content.trim()
        )
        .slice(-8)
        .map((item) => ({
          role: item.role,
          content: item.content.trim(),
        }))
    : [];

  const response = await api.post('/assistant/chat', {
    message: String(message || '').trim(),
    history: sanitizedHistory,
  });
  return response.data;
};

const AssistantService = {
  chatWithAssistant,
};

export default AssistantService;
