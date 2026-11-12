package com.norma.taller;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:sqlite::memory:"})
class FlowTest {
 @Autowired Catalog catalog;@Autowired BotEngine bot;@Autowired IntentParser parser;
 @Test void isolatedConfirmedLifecycleAndUndo(){
 String a="test-session-a",b="test-session-b";catalog.ensure(a);catalog.ensure(b);
 assertThat(catalog.all(a)).hasSize(8);
 var first=bot.process(a,"A la número 2 subila a veinte mil pesos",null);
 assertThat(first.state()).isEqualTo("AWAITING_CONFIRMATION");
 assertThat(catalog.all(a).get(1).price()).isEqualTo(18500);
 bot.process(a,"No, dije dieciocho quinientos",null);bot.process(a,"Sí",null);
 assertThat(catalog.all(a).get(1).price()).isEqualTo(18500);
 bot.process(a,"A la número 2 subila a veinte mil pesos",null);bot.process(a,"Sí",null);
 assertThat(catalog.all(a).get(1).price()).isEqualTo(20000);
 assertThat(catalog.all(b).get(1).price()).isEqualTo(18500);
 bot.process(a,"Me equivoqué",null);assertThat(catalog.all(a).get(1).price()).isEqualTo(18500);
 bot.process(a,"", "/assets/pieza-1.webp");bot.process(a,"Cuenco nuevo a quince mil pesos",null);
 assertThat(catalog.all(a)).hasSize(8);bot.process(a,"Sí",null);bot.process(a,"Sí",null);
 assertThat(catalog.all(a)).hasSize(9);
 bot.process(a,"Esta foto es para cambiar la de la pieza 2","/assets/pieza-3.webp");bot.process(a,"Sí",null);
 assertThat(catalog.all(a).get(1).imageUrl()).isEqualTo("/assets/pieza-3.webp");
 bot.process(a,"Ya vendí la pieza 2",null);bot.process(a,"No",null);assertThat(catalog.all(a).get(1).sold()).isFalse();
 bot.process(a,"Ya vendí la pieza 2",null);bot.process(a,"Sí",null);assertThat(catalog.all(a).get(1).sold()).isTrue();
 bot.process(a,"Me equivoqué",null);assertThat(catalog.all(a).get(1).sold()).isFalse();
 catalog.reset(a);assertThat(catalog.all(a)).hasSize(8);
 }
 @Test void numbers(){assertThat(parser.price("quince mil")).isEqualTo(15000);assertThat(parser.price("15 lucas")).isEqualTo(15000);assertThat(parser.price("dieciocho quinientos")).isEqualTo(18500);assertThat(parser.price("18.500")).isEqualTo(18500);assertThat(parser.price("cero")).isNull();}
 @Test void emptyStaysEmpty(){String id="empty-session";catalog.ensure(id);catalog.restore(id,java.util.List.of());catalog.ensure(id);assertThat(catalog.all(id)).isEmpty();}
 @Test void invalidSession(){assertThatThrownBy(()->catalog.ensure("../outside")).isInstanceOf(IllegalArgumentException.class);}
}
