package app.rootology.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EcocropImportToolTest {

    @Test
    void anAbsoluteLimitInsideTheOptimalRangeIsLeftEmpty() {
        assertThat(EcocropImportTool.ordered(List.of("5", "6.5", "6", "8"))).containsExactly("5", "6.5", "", "8");
        assertThat(EcocropImportTool.ordered(List.of("20", "28", "12", "25"))).containsExactly("20", "28", "12", "");
        assertThat(EcocropImportTool.ordered(List.of("6", "7", "4.5", "8"))).containsExactly("6", "7", "4.5", "8");
        assertThat(EcocropImportTool.ordered(List.of("", "7", "4.5"))).containsExactly("", "7", "4.5");
    }
}
