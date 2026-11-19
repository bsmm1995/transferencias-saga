package ec.fin.commons.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TextsTest {

  @Test
  void keepsValuesThatAlreadyFit() {
    assertThat(Texts.truncate("abc", 3)).isEqualTo("abc");
  }

  @Test
  void cutsValuesThatDoNotFit() {
    assertThat(Texts.truncate("abcdef", 4)).isEqualTo("abcd");
  }

  @Test
  void leavesNullAsNull() {
    assertThat(Texts.truncate(null, 4)).isNull();
  }

  @Test
  void rejectsANegativeLimit() {
    assertThatThrownBy(() -> Texts.truncate("abc", -1)).isInstanceOf(IllegalArgumentException.class);
  }
}
