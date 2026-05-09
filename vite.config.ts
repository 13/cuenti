import path from 'path';
import { existsSync } from 'fs';
import { Plugin, UserConfigFn } from 'vite';

import { overrideVaadinConfig } from './vite.generated';

// Vaadin 25 ships @vaadin component packages nested inside @vaadin/react-components/node_modules.
// Flow's generated-flow-imports.js still imports them as top-level bare specifiers, so we redirect
// any unresolvable @vaadin/* import to the nested location.
function vaadinNestedModulesPlugin(): Plugin {
  const rootModules = path.resolve(__dirname, 'node_modules');
  const nestedRoot = path.resolve(rootModules, '@vaadin/react-components/node_modules');

  return {
    name: 'vaadin-nested-modules',
    resolveId(id) {
      if (!id.startsWith('@vaadin/')) return null;
      if (existsSync(path.resolve(rootModules, id))) return null;
      const nested = path.resolve(nestedRoot, id);
      if (existsSync(nested)) return nested;
      return null;
    }
  };
}

const customConfig: UserConfigFn = (env) => ({
  plugins: [vaadinNestedModulesPlugin()],
});

export default overrideVaadinConfig(customConfig);
