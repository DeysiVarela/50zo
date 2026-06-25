# Cincuentazo (Java 25 + JavaFX)

Mini proyecto del juego Cincuentazo implementado con arquitectura MVC, eventos JavaFX, concurrencia y pruebas unitarias.

## Requisitos

- Java 25
- Maven 3.9+

## Ejecutar

```bash
mvn clean javafx:run
```

## Probar

```bash
mvn test
```

## Estructura

- `model`: entidades del dominio (cartas, jugadores, mazo, reglas).
- `service`: motor del juego y snapshots para la GUI.
- `event`: contratos de eventos para desacoplar UI y lógica.
- `controller`: controlador JavaFX (FXML).
- `resources/view`: interfaz con FXML (compatible con Scene Builder).

## Funcionalidades incluidas

- Selección de 1 a 3 jugadores máquina.
- Reparto inicial de 4 cartas por jugador.
- Carta inicial de mesa y contador de suma visible.
- Turnos con regla de no exceder 50.
- Operación suma/resta por jugada.
- Robar carta tras jugar para mantener 4 cartas.
- Eliminación automática de jugadores sin jugadas válidas.
- Declaración de ganador al quedar un solo jugador.
- Retardo de la máquina con hilos:
  - 2-4 segundos para pensar jugada.
  - 1-2 segundos para robar carta.

## Teclas rápidas

- `1-4`: seleccionar carta de la mano humana.
- `+` / `-`: elegir operación.
- `Enter` o `Space`: jugar carta seleccionada.

## Excepciones propias

- Marcadas (checked):
  - `GameInitializationException`
  - `InvalidMoveException`
- No marcada (unchecked):
  - `DeckExhaustedException`

## Notas

- El código está documentado con Javadoc en inglés en clases y métodos clave.
- Estructuras de datos usadas en la implementación (alineadas al enfoque del repo sugerido):
  - `Queue<Card>` con `ArrayDeque` para administrar el mazo (FIFO).
  - `Stack<Card>` para la pila de cartas en mesa (LIFO).
  - `List`/`ArrayList` para manos, historial y snapshots de GUI.
- Para exportar Javadoc HTML:

```bash
mvn javadoc:javadoc
```
