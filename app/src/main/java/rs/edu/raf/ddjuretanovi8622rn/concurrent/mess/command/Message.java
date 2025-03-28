package rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.command;

import java.util.Objects;

public sealed interface Message extends Comparable<Message> permits Command, PoisonPill {

	/// Used for sorting in a PriorityBlockingQueue
	@Override
	default int compareTo(Message o) {
		Objects.requireNonNull(o);
		return switch (this) {
			case Command _ -> switch (o) {
				case Command _ -> 0;
				case PoisonPill _ -> 1;
			};
			case PoisonPill _ -> switch (o) {
				case Command _ -> -1;
				case PoisonPill _ -> 0;
			};
		};
	}
}
