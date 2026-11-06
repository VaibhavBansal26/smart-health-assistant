import { Component, computed, input } from '@angular/core';
import { RiskLevel } from '../../core/models/models';

const RADIUS = 80;
const ARC_LENGTH = Math.PI * RADIUS; // half circle

/** Semi-circle gauge drawn with SVG. Colour is reinforced by the text label, never colour alone. */
@Component({
  selector: 'app-risk-gauge',
  template: `
    <svg viewBox="0 0 200 120" role="img" [attr.aria-label]="'Risk score ' + score() + ' out of 100, ' + label()">
      <path class="track" d="M 20 100 A 80 80 0 0 1 180 100" />
      <path class="value" [class]="'value ' + level()" d="M 20 100 A 80 80 0 0 1 180 100"
            [attr.stroke-dasharray]="dash() + ' ' + arc" />
      <text x="100" y="86" class="num" text-anchor="middle">{{ score() }}</text>
      <text x="100" y="106" class="of" text-anchor="middle">out of 100</text>
    </svg>
  `,
  styles: `
    :host { display: block; width: 100%; max-width: 260px; }
    svg { width: 100%; height: auto; }
    path { fill: none; stroke-width: 16; stroke-linecap: round; }
    .track { stroke: var(--border); }
    .value { transition: stroke-dasharray 0.5s ease; }
    .value.LOW { stroke: #16a34a; }
    .value.MODERATE { stroke: #d97706; }
    .value.HIGH { stroke: #dc2626; }
    .num { font-size: 34px; font-weight: 800; fill: var(--text); }
    .of { font-size: 11px; fill: var(--muted); }
    @media (prefers-reduced-motion: reduce) { .value { transition: none; } }
  `,
})
export class RiskGaugeComponent {
  readonly score = input.required<number>();
  readonly level = input.required<RiskLevel>();
  protected readonly arc = ARC_LENGTH;

  protected readonly dash = computed(() => (Math.max(0, Math.min(100, this.score())) / 100) * ARC_LENGTH);
  protected readonly label = computed(
    () => ({ LOW: 'lower risk', MODERATE: 'moderate risk', HIGH: 'higher risk' })[this.level()],
  );
}
