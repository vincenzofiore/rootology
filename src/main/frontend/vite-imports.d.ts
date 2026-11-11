/**
 * The `?worker&url` suffix is Vite's (#66): it emits the module as its own asset and hands back the URL.
 * Vaadin's generated tsconfig.json has no `vite/client` types, so the import is declared here.
 */
declare module '*?worker&url' {
  const url: string;
  export default url;
}

/**
 * A stylesheet imported by a module, which Vite adds to the page (#66): MapLibre's own CSS. Vaadin's
 * generated types.d.ts declares only the `?inline` form.
 */
declare module '*.css';
