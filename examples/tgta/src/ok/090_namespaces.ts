namespace N {
  export const value = 1;

  export namespace Nested {
    export function pick(): number {
      return value;
    }
  }
}

declare global {
  interface TGTAWindow {
    tgtaReady: boolean;
  }
}

declare module "pkg-augment" {
  export interface PluginConfig {
    enabled: boolean;
  }
}

void N;
export {};
