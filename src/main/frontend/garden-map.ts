/**
 * The gardens of a space on a map (#66): MapLibre GL JS over the OpenFreeMap tiles, bundled by Vite,
 * never from a CDN. One element, `rootology-garden-map`, driven by properties from GardenMap.java and
 * reporting back with DOM events: `garden-selected` (a marker was clicked), `garden-open`, `garden-edit` and
 * `garden-delete` (a popup's buttons, the same actions as the table's row, so the map alone is enough) and
 * `add-here` (a click on empty ground while the person may add a garden).
 *
 * Light DOM on purpose: every style lives in META-INF/resources/styles/rootology.css, and a shadow root
 * would keep those rules away from the map's own boxes. The texts come from the server already translated.
 */
import { LitElement, html, type PropertyValues } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { ref } from 'lit/directives/ref.js';
import { Map as MapLibreMap, Marker, Popup, AttributionControl, NavigationControl, setWorkerUrl } from 'maplibre-gl';
// The worker has to be given to MapLibre explicitly: its own default is a path relative to the bundle,
// which does not exist once Vite has built it. `?worker&url` makes Vite emit it among our own assets.
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import 'maplibre-gl/dist/maplibre-gl.css';
// The same icons as the row buttons in the rest of the app (Vaadin's own set, already in the bundle).
import '@vaadin/icon';
import '@vaadin/icons';

setWorkerUrl(workerUrl);

/**
 * One garden as the popup shows it: the texts are already worded and translated by the server; the crops
 * growing and the questions waiting are counts, shown beside their icons.
 */
interface GardenPin {
  id: string;
  name: string;
  town: string;
  altitude: string;
  /** MET Norway's symbol code of the stored hour nearest to now, or null: its icon (#67). */
  weatherSymbol: string | null;
  /** The temperature and precipitation already worded, or null when there is no such hour. */
  weatherText: string | null;
  crops: number;
  waiting: number;
}

/** The gardens that share one rounded position, about 1 km: one marker, one popup listing them all. */
interface GardenGroup {
  latitude: number;
  longitude: number;
  title: string;
  /** The coordinates, already worded as approximate. */
  position: string;
  gardens: GardenPin[];
}

interface Labels {
  open: string;
  edit: string;
  delete: string;
  /** The words for the icons' tooltips: altitude, weather now, crops growing, questions waiting. */
  altitude: string;
  weather: string;
  /** MET Norway's credit, in the map's attribution while a popup shows its weather (#67). */
  weatherCredit: string;
  crops: string;
  waiting: string;
  addHere: string;
  unavailable: string;
  water: string;
}

/** With no garden at all the map opens on Italy, never on the person's position without asking. */
const ITALY = { center: [12.5, 42.4] as [number, number], zoom: 4.6 };

/** A single garden is not zoomed further in than this: a place, not a doorstep. */
const MAX_FIT_ZOOM = 12;

/** How close a place found by name is shown: a town and its fields, where a garden can be pointed at. */
const PLACE_ZOOM = 13;

/** The water of the OpenMapTiles schema as the Liberty style draws it: sea, lakes, rivers, ponds. */
const WATER_LAYER = 'water';

/**
 * The altitude icon: a mountain drawn in the text colour, so it follows light and dark mode. Our own drawing,
 * inline, so no icon set is added yet.
 */
const MOUNTAIN_ICON = `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
  stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
  <path d="M3 20 9.5 8l3.5 6 2.5-4L21 20Z"/><path d="m8 11 1.5 1.5L11 11"/></svg>`;

/** The crops icon: a sprout, our own drawing like the mountain (Vaadin's icons have no plant). */
const SPROUT_ICON = `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
  stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
  <path d="M12 21v-9"/><path d="M12 12C12 8 9 6 5 6c0 4 3 6 7 6Z"/><path d="M12 10c0-3 2-5 6-5 0 3-2 5-6 5Z"/></svg>`;

/** The questions waiting icon: the hourglass of the Escalations menu entry. */
const WAITING_ICON = 'vaadin:hourglass';

/** The room a popup keeps from the map's edges, in pixels. */
const POPUP_MARGIN = 12;

@customElement('rootology-garden-map')
export class GardenMap extends LitElement {
  @property({ type: Array }) groups: GardenGroup[] = [];
  @property({ type: Object }) labels: Labels = {
    open: '', edit: '', delete: '', altitude: '', weather: '', weatherCredit: '', crops: '', waiting: '', addHere: '',
    unavailable: '', water: ''
  };
  @property({ type: String }) styleUrl = '';
  /** Whether the person may add, edit and delete gardens; the server checks every such event again. */
  @property({ type: Boolean }) contributor = false;
  /** Set by the server to centre a garden and open its popup; `nonce` makes a repeated pick a change. */
  @property({ type: Object }) centre: { id: string; nonce: number } | null = null;

  @state() private failed = false;

  private map?: MapLibreMap;
  private loaded = false;
  private container?: HTMLDivElement;
  private markers: Marker[] = [];
  private popup?: Popup;
  private addPopup?: Popup;
  private attribution?: AttributionControl;
  /** Whether the attribution now carries MET Norway's credit. */
  private credited = false;

  // Every style lives in rootology.css, so there is no shadow root.
  protected override createRenderRoot(): HTMLElement {
    return this;
  }

  protected override render() {
    return html`
      <div class="rootology-map">
        <div class="rootology-map-canvas" ${ref(this.build)}></div>
        ${this.failed ? html`<p class="rootology-map-unavailable">${this.labels.unavailable}</p>` : ''}
      </div>
    `;
  }

  /** The map is built once, on the real element, as soon as the style is known; the rest is property changes. */
  private build = (element?: Element) => {
    if (!(element instanceof HTMLDivElement) || this.container === element) {
      return;
    }
    this.container = element;
    this.start();
  };

  private start() {
    if (this.map || !this.container || !this.styleUrl) {
      return;
    }
    try {
      this.map = new MapLibreMap({
        container: this.container,
        style: this.styleUrl,
        center: ITALY.center,
        zoom: ITALY.zoom,
        attributionControl: false,
      });
    } catch (error) {
      // No WebGL2 (an old browser, a blocked GPU) is the same as no map: say so, and the table stays.
      console.warn('The gardens map could not start', error);
      this.failed = true;
      return;
    }
    // The Liberty style JSON declares no attribution, but the TileJSON of its tiles does, and MapLibre
    // shows it: "OpenFreeMap © OpenMapTiles Data from OpenStreetMap", with the three links. Checked rendering;
    // nothing is added on top, so it is not said twice, except MET Norway's credit when its weather is shown.
    this.credit();
    // Zoom buttons for whoever prefers them to the wheel or two fingers; no compass, the map never rotates.
    this.map.addControl(new NavigationControl({ showCompass: false }), 'top-right');
    this.map.dragRotate.disable();
    this.map.touchZoomRotate.disableRotation();
    this.map.on('load', () => {
      this.loaded = true;
      this.draw();
      this.fit();
    });
    // Before the first load an error means the style or its tiles cannot come: say so, keep the table.
    this.map.on('error', () => {
      if (!this.loaded) {
        this.failed = true;
      }
    });
    this.map.on('click', (event) => this.emptyGround(event.lngLat.lat, event.lngLat.lng));
    this.draw();
  }

  protected override updated(changed: PropertyValues<this>) {
    if (changed.has('styleUrl')) {
      this.start();
    }
    if (changed.has('groups') || changed.has('labels')) {
      this.credit();
    }
    if (changed.has('groups') || changed.has('contributor')) {
      this.draw();
      if (changed.has('groups') && this.loaded) {
        this.fit();
      }
    }
    if (changed.has('centre') && this.centre) {
      this.centreOn(this.centre.id);
    }
  }

  /** Closes the map when the element goes away, so its WebGL context does not pile up. */
  override disconnectedCallback() {
    super.disconnectedCallback();
    this.map?.remove();
    this.map = undefined;
    this.attribution = undefined;
    this.credited = false;
    this.loaded = false;
  }

  /** Back in the page (a Vaadin re-render, a move between parents): the map is built again. */
  override connectedCallback() {
    super.connectedCallback();
    this.start();
  }

  /**
   * The attribution in the corner, with "Data from MET Norway" added while any popup shows the weather now:
   * its terms ask for the credit wherever the data appears, visibly, not only in a tooltip (#67).
   */
  private credit() {
    if (!this.map) {
      return;
    }
    const weather = this.groups.some((group) => group.gardens.some((garden) => garden.weatherText))
      && this.labels.weatherCredit !== '';
    if (this.attribution && weather === this.credited) {
      return;
    }
    if (this.attribution) {
      this.map.removeControl(this.attribution);
    }
    const link = document.createElement('a');
    link.href = 'https://api.met.no/';
    link.target = '_blank';
    link.rel = 'noopener';
    link.textContent = this.labels.weatherCredit;
    this.attribution = new AttributionControl({ compact: true, customAttribution: weather ? link.outerHTML : undefined });
    this.map.addControl(this.attribution, 'bottom-right');
    this.credited = weather;
  }

  private draw() {
    if (!this.map) {
      return;
    }
    this.markers.forEach((marker) => marker.remove());
    this.markers = [];
    this.popup?.remove();
    for (const group of this.groups) {
      this.markers.push(this.markerFor(group));
    }
  }

  private markerFor(group: GardenGroup): Marker {
    const element = document.createElement('button');
    element.type = 'button';
    element.className = 'rootology-map-pin';
    if (group.gardens.length > 1) {
      element.classList.add('rootology-map-pin-count');
      element.textContent = String(group.gardens.length);
    }
    element.title = group.gardens.map((garden) => garden.name).join(', ');
    element.addEventListener('click', (event) => {
      event.stopPropagation();
      if (group.gardens.length === 1) {
        this.emit('garden-selected', { id: group.gardens[0].id });
      }
      this.openPopup(group, group.longitude, group.latitude);
    });
    return new Marker({ element }).setLngLat([group.longitude, group.latitude]).addTo(this.map!);
  }

  /** Clicking empty ground offers to add a garden there; a tap on a phone pans, so it never opens the dialog. */
  private emptyGround(latitude: number, longitude: number) {
    if (!this.contributor || !this.map) {
      return;
    }
    let content: HTMLElement;
    if (this.onWater(latitude, longitude)) {
      // The point clicked, before any rounding: water there is no place for a garden (#66).
      content = document.createElement('p');
      content.className = 'rootology-map-water';
      content.textContent = this.labels.water;
    } else {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'rootology-map-add';
      button.textContent = this.labels.addHere;
      button.addEventListener('click', () => {
        this.addPopup?.remove();
        this.emit('add-here', { latitude, longitude });
      });
      content = button;
    }
    this.addPopup?.remove();
    this.addPopup = new Popup({ closeButton: true, closeOnClick: true, offset: 8 })
      .setLngLat([longitude, latitude])
      .setDOMContent(content)
      .addTo(this.map);
    this.keepInside(this.addPopup);
  }

  /**
   * Whether the map draws water at a point, as the person sees it at this zoom. Without the layer (another
   * style, tiles still loading) the point counts as land: the dialog's own check on save is still there.
   */
  private onWater(latitude: number, longitude: number): boolean {
    if (!this.map || !this.map.getLayer(WATER_LAYER)) {
      return false;
    }
    const point = this.map.project([longitude, latitude]);
    return this.map.queryRenderedFeatures(point, { layers: [WATER_LAYER] }).length > 0;
  }

  /**
   * A place found by name in the page's search (#66): the map goes there and, for whoever may add a garden,
   * offers to add one at that point. Called by the server with the place's coordinates.
   */
  showPlace(latitude: number, longitude: number) {
    if (!this.map) {
      return;
    }
    this.popup?.remove();
    this.map.once('moveend', () => this.emptyGround(latitude, longitude));
    this.map.flyTo({ center: [longitude, latitude], zoom: PLACE_ZOOM, duration: 800 });
  }

  /**
   * MapLibre keeps a popup inside the map when it can choose the side, but near a corner it still sticks out,
   * and the map clips it. Pans the map just enough to bring the whole popup into view.
   */
  private keepInside(popup: Popup) {
    requestAnimationFrame(() => {
      if (!this.map || !this.container || !popup.isOpen()) {
        return;
      }
      const box = popup.getElement().getBoundingClientRect();
      const frame = this.container.getBoundingClientRect();
      const dx = box.left < frame.left + POPUP_MARGIN ? box.left - frame.left - POPUP_MARGIN
        : box.right > frame.right - POPUP_MARGIN ? box.right - frame.right + POPUP_MARGIN : 0;
      const dy = box.top < frame.top + POPUP_MARGIN ? box.top - frame.top - POPUP_MARGIN
        : box.bottom > frame.bottom - POPUP_MARGIN ? box.bottom - frame.bottom + POPUP_MARGIN : 0;
      if (dx !== 0 || dy !== 0) {
        this.map.panBy([dx, dy], { duration: 250 });
      }
    });
  }

  private openPopup(group: GardenGroup, longitude: number, latitude: number) {
    if (!this.map) {
      return;
    }
    const content = document.createElement('div');
    content.className = 'rootology-map-popup';
    // Gardens on one point share its position: said once under the group's title, otherwise in the garden.
    const shared = group.gardens.length > 1;
    if (shared) {
      const title = document.createElement('p');
      title.className = 'rootology-map-popup-title';
      title.textContent = group.title;
      content.append(title, this.position(group.position));
    }
    for (const garden of group.gardens) {
      content.append(this.entry(garden, shared ? null : group.position));
    }

    this.popup?.remove();
    this.popup = new Popup({ closeButton: true, closeOnClick: false, maxWidth: '18rem', offset: 12 })
      .setLngLat([longitude, latitude])
      .setDOMContent(content)
      .addTo(this.map);
    this.popup.on('close', () => {
      this.popup = undefined;
    });
    this.keepInside(this.popup);
  }

  /**
   * One garden in a popup, one kind of information per line: the name (it selects the row), the town with
   * its altitude, the position when the garden has the point to itself, the weather now, what grows and the
   * questions waiting, and last the table row's actions (Open, Edit, Delete).
   */
  private entry(garden: GardenPin, position: string | null): HTMLElement {
    const box = document.createElement('div');
    box.className = 'rootology-map-popup-garden';

    const name = document.createElement('button');
    name.type = 'button';
    name.className = 'rootology-map-popup-name';
    name.textContent = garden.name;
    name.addEventListener('click', () => {
      this.popup?.remove();
      this.emit('garden-selected', { id: garden.id });
    });

    const details = document.createElement('div');
    details.className = 'rootology-map-popup-details';
    const place = document.createElement('div');
    place.className = 'rootology-map-popup-facts';
    place.append(this.line(garden.town), this.fact(MOUNTAIN_ICON, this.labels.altitude, garden.altitude));
    details.append(place);
    if (position) {
      details.append(this.position(position));
    }
    const weather = this.weatherIcon(garden.weatherSymbol);
    if (weather && garden.weatherText) {
      details.append(this.fact(weather, this.labels.weather, garden.weatherText));
    }
    const facts = document.createElement('div');
    facts.className = 'rootology-map-popup-facts';
    facts.append(this.fact(SPROUT_ICON, this.labels.crops, String(garden.crops)),
      this.fact(WAITING_ICON, this.labels.waiting, String(garden.waiting),
        garden.waiting > 0 ? 'rootology-map-popup-attention' : undefined));
    details.append(facts);

    const actions = document.createElement('div');
    actions.className = 'rootology-map-popup-actions';
    actions.append(this.action('vaadin:arrow-right', this.labels.open, 'garden-open', garden.id));
    if (this.contributor) {
      actions.append(this.action('vaadin:edit', this.labels.edit, 'garden-edit', garden.id),
        this.action('vaadin:trash', this.labels.delete, 'garden-delete', garden.id, 'rootology-map-popup-delete'));
    }

    box.append(name, details, actions);
    return box;
  }

  /**
   * The weather icon, the same self-served MIT icons as the weather card (#67), or nothing when the code is
   * missing or is not one of their names: the code is data from outside, so it never goes into markup as is.
   */
  private weatherIcon(symbol: string | null): string | null {
    if (!symbol || !/^[a-z_]+$/.test(symbol)) {
      return null;
    }
    return `<img src="weather-icons/${symbol}.svg" width="16" height="16" alt="" aria-hidden="true" focusable="false">`;
  }

  /** The rounded position, about 1 km, as a small secondary line. */
  private position(text: string): HTMLParagraphElement {
    const p = this.line(text);
    p.className = 'rootology-map-popup-position';
    return p;
  }

  private line(text: string): HTMLParagraphElement {
    const p = document.createElement('p');
    p.textContent = text;
    return p;
  }

  /**
   * A value with its icon instead of a word (#66): the word is the tooltip on hover and what a screen reader
   * says, so nothing is lost without the icon. The icon is one of our inline drawings or a {@code vaadin:} name.
   */
  private fact(icon: string, label: string, value: string, extra?: string): HTMLParagraphElement {
    const p = document.createElement('p');
    p.className = 'rootology-map-popup-fact';
    if (extra) {
      p.classList.add(extra);
    }
    p.title = label;
    const symbol = document.createElement('span');
    symbol.className = 'rootology-map-popup-icon';
    if (icon.startsWith('vaadin:')) {
      const vaadinIcon = document.createElement('vaadin-icon');
      vaadinIcon.setAttribute('icon', icon);
      vaadinIcon.setAttribute('aria-hidden', 'true');
      symbol.append(vaadinIcon);
    } else {
      symbol.innerHTML = icon;
    }
    const hidden = document.createElement('span');
    hidden.className = 'rootology-visually-hidden';
    hidden.textContent = `${label}: `;
    const text = document.createElement('span');
    text.textContent = value;
    p.append(symbol, hidden, text);
    return p;
  }

  /**
   * A popup button that hands one garden's id to the server; editing and deleting close the popup first. An
   * icon, as everywhere in the app's rows: the word is its tooltip and what a screen reader says.
   */
  private action(icon: string, label: string, type: string, id: string, extra?: string): HTMLButtonElement {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'rootology-map-popup-action';
    if (extra) {
      button.classList.add(extra);
    }
    button.title = label;
    button.setAttribute('aria-label', label);
    const symbol = document.createElement('vaadin-icon');
    symbol.setAttribute('icon', icon);
    symbol.setAttribute('aria-hidden', 'true');
    button.append(symbol);
    button.addEventListener('click', () => {
      if (type !== 'garden-open') {
        this.popup?.remove();
      }
      this.emit(type, { id });
    });
    return button;
  }

  /** Centres the map on a garden and opens its popup, when the table selects its row. */
  private centreOn(id: string) {
    const group = this.groups.find((candidate) => candidate.gardens.some((garden) => garden.id === id));
    if (!group || !this.map) {
      return;
    }
    // The marker settles in the lower part of the frame, so its popup above it has room.
    const offset: [number, number] = [0, (this.container?.clientHeight ?? 0) / 4];
    this.map.once('moveend', () => this.openPopup(group, group.longitude, group.latitude));
    this.map.easeTo({ center: [group.longitude, group.latitude], zoom: Math.max(this.map.getZoom(), 11), offset });
  }

  /** All the gardens in view; a single one stops at a place, not a doorstep. */
  private fit() {
    if (!this.map) {
      return;
    }
    if (this.groups.length === 0) {
      this.map.jumpTo({ center: ITALY.center, zoom: ITALY.zoom });
      return;
    }
    const longitudes = this.groups.map((group) => group.longitude);
    const latitudes = this.groups.map((group) => group.latitude);
    this.map.fitBounds(
      [
        [Math.min(...longitudes), Math.min(...latitudes)],
        [Math.max(...longitudes), Math.max(...latitudes)],
      ],
      { padding: 48, maxZoom: MAX_FIT_ZOOM, duration: 0 },
    );
  }

  private emit(type: string, detail: unknown) {
    this.dispatchEvent(new CustomEvent(type, { detail, bubbles: true, composed: true }));
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'rootology-garden-map': GardenMap;
  }
}
