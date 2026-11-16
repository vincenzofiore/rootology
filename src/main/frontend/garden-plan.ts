/**
 * The plan of a garden (#90): a rectangle in metres with its areas as boxes, drawn in SVG by the Lit element
 * `rootology-garden-plan` and driven by properties from GardenPlanBoard.java. The component only draws what the
 * page computed; it reports back with DOM events: `area-selected` (a box was tapped, its card is marked and
 * scrolled to) and `area-moved` (a box was dragged, resized by its corner handle, or moved with the keys, and
 * let go; the server stores it and shows the plan again).
 *
 * Light DOM on purpose, as the map is: every style lives in META-INF/resources/styles/rootology.css, whose
 * custom properties carry the colours of light and dark. The texts come from the server already translated.
 */
import { LitElement, html, svg, type PropertyValues, type TemplateResult } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { ref } from 'lit/directives/ref.js';
import { unsafeSVG } from 'lit/directives/unsafe-svg.js';

/** The plan's own size in metres. */
interface PlanSize {
  width: number;
  depth: number;
}

/** An area on the plan: what it says, where it sits and how big it is, in metres from the plan's top-left. */
interface PlanBox {
  id: string;
  name: string;
  /** The URL of the drawing of its kind (`icons/areas/`). */
  icon: string;
  /** Its size already worded ("2 × 1 m"), shown when the box has room for it. */
  size: string;
  /** Name, size and position, already worded: what a screen reader says. */
  label: string;
  x: number;
  y: number;
  width: number;
  depth: number;
}

/** The smallest side an area can have, as the service has it. */
const MIN_SIDE = 0.1;

/** How long the keys of one box are collected before they are saved. */
const KEY_DELAY = 500;

/** A corner handle's side, in pixels: large enough for a finger. */
const HANDLE = 16;

/** The inside of each area kind's drawing, by URL, shared by every plan of the page. */
const DRAWINGS = new Map<string, string>();

@customElement('rootology-garden-plan')
export class GardenPlan extends LitElement {
  /** The plan to draw; nothing is drawn without one. */
  @property({ type: Object }) plan: PlanSize | null = null;
  @property({ type: Array }) boxes: PlanBox[] = [];
  /** The area the page marked, for example from its card; `nonce` makes a repeated pick a change. */
  @property({ type: Object }) selected: { id: string; nonce: number } | null = null;
  /** Whether the person may move and resize: a guest only looks, and the server checks every event again. */
  @property({ type: Boolean }) contributor = false;
  /** What a screen reader calls the drawing as a whole. */
  @property({ type: String }) planLabel = '';

  /** How many pixels one metre takes on screen, so text and handles stay the same size at any width. */
  @state() private pxPerMetre = 24;

  private svgElement?: SVGSVGElement;
  private resize?: ResizeObserver;
  private readonly timers = new Map<string, number>();
  private drag?: { mode: 'move' | 'resize'; box: PlanBox; from: { x: number; y: number } };

  // Every style lives in rootology.css, so there is no shadow root.
  protected override createRenderRoot(): HTMLElement {
    return this;
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    this.resize?.disconnect();
    this.resize = undefined;
    this.untrack();
    this.timers.forEach((timer) => window.clearTimeout(timer));
    this.timers.clear();
  }

  protected override render() {
    const plan = this.plan;
    if (!plan || plan.width <= 0 || plan.depth <= 0) {
      return html``;
    }
    const ratio = plan.width / plan.depth;
    return html`
      <div class="rootology-plan"
           style="--rootology-plan-ratio: ${ratio}">
        <svg class="rootology-plan-svg" viewBox="0 0 ${plan.width} ${plan.depth}"
             preserveAspectRatio="xMidYMid meet" role="group" aria-label=${this.planLabel}
             ${ref(this.measure)}>
          <defs>
            <pattern id="rootology-plan-grid" width="1" height="1" patternUnits="userSpaceOnUse">
              <path class="rootology-plan-grid-line" d="M 1 0 L 0 0 0 1" fill="none"></path>
            </pattern>
          </defs>
          <rect class="rootology-plan-ground" x="0" y="0" width=${plan.width} height=${plan.depth}
                fill="url(#rootology-plan-grid)"></rect>
          ${this.boxes.map((box) => this.drawBox(box))}
        </svg>
      </div>
    `;
  }

  protected override updated(changed: PropertyValues<this>) {
    if (changed.has('plan')) {
      this.measureAgain();
    }
    if (changed.has('selected') && this.selected) {
      const element = this.querySelector(`[data-id="${this.selected.id}"]`);
      (element as SVGGElement | null)?.focus();
    }
  }

  /** One area on the plan: a rounded box with its kind, its name and, when it fits, its size. */
  private drawBox(box: PlanBox): TemplateResult {
    const scale = this.pxPerMetre;
    /** Pixels as metres, so a handle or a line stays the same size however big the plan is. */
    const px = (value: number) => value / scale;
    const widthPx = box.width * scale;
    const depthPx = box.depth * scale;
    const pad = px(5);
    const font = px(12);
    const small = px(10.5);
    const icon = px(16);
    const withIcon = widthPx >= 72 && depthPx >= 34;
    const withSize = depthPx >= 40 && widthPx >= 64;
    const left = box.x + pad + (withIcon ? icon + px(4) : 0);
    const room = (box.x + box.width - pad) - left;
    const name = this.cut(box.name, room, 12);
    const size = this.cut(box.size, room, 10.5);
    const radius = Math.min(px(6), box.width / 5, box.depth / 5);
    const selected = this.selected?.id === box.id;
    return svg`
      <g class="rootology-plan-box ${selected ? 'rootology-plan-box-selected' : ''}" data-id=${box.id}
         tabindex="0" aria-label=${box.label}
         @pointerdown=${(event: PointerEvent) => this.down(event, box, 'move')}
         @keydown=${(event: KeyboardEvent) => this.keydown(event, box)}>
        <rect class="rootology-plan-box-rect" x=${box.x} y=${box.y} width=${box.width} height=${box.depth}
              rx=${radius}></rect>
        ${withIcon && this.drawing(box.icon)
          ? svg`<svg class="rootology-plan-box-icon" x=${box.x + pad} y=${box.y + pad} width=${icon} height=${icon}
                     viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.6"
                     stroke-linecap="round" stroke-linejoin="round">${unsafeSVG(this.drawing(box.icon))}</svg>`
          : ''}
        ${name
          ? svg`<text class="rootology-plan-box-name" x=${left} y=${box.y + pad + font}
                     font-size=${font}>${name}</text>`
          : ''}
        ${withSize && size
          ? svg`<text class="rootology-plan-box-size" x=${left} y=${box.y + pad + font + small * 1.5}
                     font-size=${small}>${size}</text>`
          : ''}
        ${this.contributor
          ? svg`<rect class="rootology-plan-handle" x=${box.x + box.width - px(HANDLE)}
                      y=${box.y + box.depth - px(HANDLE)} width=${px(HANDLE)} height=${px(HANDLE)}
                      rx=${px(3)} @pointerdown=${(event: PointerEvent) => this.down(event, box, 'resize')}></rect>`
          : ''}
      </g>
    `;
  }

  /**
   * The inside of an area kind's drawing (#85), drawn in the plan itself rather than as an <image>: an image
   * did not render inside the scaled plan, and inline it takes the text colour in light and dark. The files are
   * our own static drawings, all on a 48 × 48 view box; each is fetched once for every plan of the page.
   */
  private drawing(url: string): string {
    const known = DRAWINGS.get(url);
    if (known !== undefined) {
      return known;
    }
    DRAWINGS.set(url, '');
    fetch(url)
      .then((response) => (response.ok ? response.text() : ''))
      .then((text) => {
        const inside = text.match(/<svg[^>]*>([\s\S]*)<\/svg>/);
        DRAWINGS.set(url, inside ? inside[1] : '');
        this.requestUpdate();
      })
      .catch(() => undefined);
    return '';
  }

  /** A text cut to the room it has, with an ellipsis when it does not fit; nothing when there is no room. */
  private cut(text: string, roomMetres: number, fontPx: number): string {
    const room = Math.floor((roomMetres * this.pxPerMetre) / (fontPx * 0.55));
    if (room < 2) {
      return '';
    }
    return text.length <= room ? text : `${text.slice(0, room - 1)}…`;
  }

  /** The SVG is measured once it is in the page, and again whenever its width changes. */
  private measure = (element?: Element) => {
    if (!(element instanceof SVGSVGElement)) {
      return;
    }
    this.svgElement = element;
    this.resize ??= new ResizeObserver(() => this.measureAgain());
    this.resize.disconnect();
    this.resize.observe(element);
    this.measureAgain();
  };

  private measureAgain() {
    const svg = this.svgElement;
    const plan = this.plan;
    if (!svg || !plan || plan.width <= 0 || plan.depth <= 0) {
      return;
    }
    const box = svg.getBoundingClientRect();
    // The drawing keeps its corners' proportions (xMidYMid meet), so the smaller of the two scales is the one.
    const scale = Math.min(box.width / plan.width, box.height / plan.depth);
    if (scale > 0 && Math.abs(scale - this.pxPerMetre) > 0.05) {
      this.pxPerMetre = scale;
    }
  }

  /** A point of the page in the plan's metres, whatever the plan's size on screen is. */
  private toPlan(event: PointerEvent): { x: number; y: number } | null {
    const svg = this.svgElement;
    const matrix = svg?.getScreenCTM();
    if (!svg || !matrix) {
      return null;
    }
    const point = svg.createSVGPoint();
    point.x = event.clientX;
    point.y = event.clientY;
    const local = point.matrixTransform(matrix.inverse());
    return { x: local.x, y: local.y };
  }

  /** Picking a box up, by its body to move it or by its corner handle to resize it. */
  private down(event: PointerEvent, box: PlanBox, mode: 'move' | 'resize') {
    if (!this.contributor || event.button !== 0) {
      return;
    }
    const from = this.toPlan(event);
    if (!from) {
      return;
    }
    // No scrolling, no text selection while a box is being moved (#90).
    event.preventDefault();
    event.stopPropagation();
    this.drag = { mode, box: { ...box }, from };
    window.addEventListener('pointermove', this.moving);
    window.addEventListener('pointerup', this.up);
    window.addEventListener('pointercancel', this.up);
  }

  private moving = (event: PointerEvent) => {
    const drag = this.drag;
    const plan = this.plan;
    if (!drag || !plan) {
      return;
    }
    const point = this.toPlan(event);
    if (!point) {
      return;
    }
    const dx = point.x - drag.from.x;
    const dy = point.y - drag.from.y;
    const start = drag.box;
    if (drag.mode === 'move') {
      this.updateBox(start.id, {
        x: this.snap(start.x + dx, 0, plan.width - start.width),
        y: this.snap(start.y + dy, 0, plan.depth - start.depth),
      });
    } else {
      this.updateBox(start.id, {
        width: this.snap(start.width + dx, MIN_SIDE, plan.width - start.x),
        depth: this.snap(start.depth + dy, MIN_SIDE, plan.depth - start.y),
      });
    }
  };

  private up = () => {
    const drag = this.drag;
    this.untrack();
    this.drag = undefined;
    if (!drag) {
      return;
    }
    const box = this.boxes.find((candidate) => candidate.id === drag.box.id);
    if (!box) {
      return;
    }
    const moved = box.x !== drag.box.x || box.y !== drag.box.y
      || box.width !== drag.box.width || box.depth !== drag.box.depth;
    if (moved) {
      this.save(drag.box.id);
    } else if (drag.mode === 'move') {
      // A tap, not a drag: the box is chosen, and its card is marked and scrolled to.
      this.emit('area-selected', { id: drag.box.id });
    }
  };

  /** Arrows move a box, Shift+arrows by a metre, Alt+arrows resize it; the plan is saved after a pause. */
  private keydown(event: KeyboardEvent, box: PlanBox) {
    const plan = this.plan;
    if (!this.contributor || !plan) {
      return;
    }
    const keys = ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'];
    if (!keys.includes(event.key)) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    const horizontal = event.key === 'ArrowLeft' || event.key === 'ArrowRight';
    const sign = event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? -1 : 1;
    const step = event.shiftKey ? 1 : MIN_SIDE;
    if (event.altKey) {
      this.updateBox(box.id, horizontal
        ? { width: this.snap(box.width + sign * MIN_SIDE, MIN_SIDE, plan.width - box.x) }
        : { depth: this.snap(box.depth + sign * MIN_SIDE, MIN_SIDE, plan.depth - box.y) });
    } else {
      this.updateBox(box.id, horizontal
        ? { x: this.snap(box.x + sign * step, 0, plan.width - box.width) }
        : { y: this.snap(box.y + sign * step, 0, plan.depth - box.depth) });
    }
    const timer = this.timers.get(box.id);
    if (timer !== undefined) {
      window.clearTimeout(timer);
    }
    this.timers.set(box.id, window.setTimeout(() => this.save(box.id), KEY_DELAY));
  }

  private updateBox(id: string, patch: Partial<PlanBox>) {
    this.boxes = this.boxes.map((box) => (box.id === id ? { ...box, ...patch } : box));
  }

  /** A length on the decimetre grid, inside the plan. */
  private snap(value: number, min: number, max: number): number {
    const snapped = Math.round(value * 10) / 10;
    return Math.min(Math.max(snapped, min), Math.max(min, max));
  }

  /** Tells the server where a box ended up, once. */
  private save(id: string) {
    const timer = this.timers.get(id);
    if (timer !== undefined) {
      window.clearTimeout(timer);
      this.timers.delete(id);
    }
    const box = this.boxes.find((candidate) => candidate.id === id);
    if (!box) {
      return;
    }
    this.emit('area-moved', { id, x: box.x, y: box.y, width: box.width, depth: box.depth });
  }

  private untrack() {
    window.removeEventListener('pointermove', this.moving);
    window.removeEventListener('pointerup', this.up);
    window.removeEventListener('pointercancel', this.up);
  }

  private emit(type: string, detail: unknown) {
    this.dispatchEvent(new CustomEvent(type, { detail, bubbles: true, composed: true }));
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'rootology-garden-plan': GardenPlan;
  }
}
