# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
]);
```

You can also install [eslint-plugin-react-x](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x';
import reactDom from 'eslint-plugin-react-dom';

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
]);
```

## HANBAY frontend — stay time and inventory

This client serves the HANBAY application in addition to the template setup above.
Run `npm run dev` for local Vite; backend must be available through the configured API proxy.

- Search `/rooms/search`: room-name links open `/room-types/:roomTypeId`; the badge uses
  `availableRooms` for the submitted dates, not `readyRooms` now. Search refreshes every30s/on focus/reconnect.
- `/bookings/new`: calendar-night counting is shared with room detail and summary/payment.
- `GET /api/v1/stay-policy`: shared timezone/server clock and14:00/12:00 schedule; policy refresh 60s.
- Same-day booking after14:00 is valid until the separate cutoff; expiredAt remains its own payment deadline.
- Front desk supports early-arrival reason, late-arrival window, overdue checkout note and today-only early checkout.

Use the [16 changed FE flows](../docs/testing/stay-time-fe-checklist.md) for focused manual testing;
[full UAT](../docs/testing/web-uat-guide.md) covers the whole application.
Rules/configuration: [stay time and inventory](../docs/23-stay-time-and-inventory.md).
These checklists describe expectations; no manual pass is implied.
