package app.rootology.ui;

import com.vaadin.flow.component.Component;

import java.util.List;

/** How a group of chips behaves, wherever the page shows one (#120): the suggested questions and a choice's options. */
final class ChipGroup {

    private ChipGroup() {
    }

    /**
     * One Tab stop for a group of chips (#120): the first chip (or the last one focused) carries tabindex 0,
     * the others -1, and the arrow keys move between them. The keydown handler stays in the browser and only
     * moves the focus; Enter and Space still click the chip, as a button always does.
     */
    static void roving(Component group, String label) {
        group.getElement().setAttribute("role", "group");
        group.getElement().setAttribute("aria-label", label);
        List<Component> chips = group.getChildren().toList();
        for (int i = 0; i < chips.size(); i++) {
            chips.get(i).getElement().setAttribute("tabindex", i == 0 ? "0" : "-1");
        }
        group.getElement().executeJs("""
                const group = this;
                if (group.__roving) return;
                group.__roving = true;
                const chips = () => Array.from(group.querySelectorAll('.rootology-ask-chip'));
                group.addEventListener('focusin', (event) => {
                    const target = event.target.closest('.rootology-ask-chip');
                    if (!target) return;
                    chips().forEach((chip) => chip.setAttribute('tabindex', chip === target ? '0' : '-1'));
                });
                group.addEventListener('keydown', (event) => {
                    const target = event.target.closest('.rootology-ask-chip');
                    if (!target) return;
                    const items = chips();
                    const index = items.indexOf(target);
                    if (index < 0) return;
                    let next;
                    switch (event.key) {
                        case 'ArrowRight': case 'ArrowDown': next = index + 1; break;
                        case 'ArrowLeft': case 'ArrowUp': next = index - 1; break;
                        case 'Home': next = 0; break;
                        case 'End': next = items.length - 1; break;
                        default: return;
                    }
                    event.preventDefault();
                    next = Math.max(0, Math.min(items.length - 1, next));
                    items.forEach((chip, i) => chip.setAttribute('tabindex', i === next ? '0' : '-1'));
                    items[next].focus();
                });
                """);
    }
}
