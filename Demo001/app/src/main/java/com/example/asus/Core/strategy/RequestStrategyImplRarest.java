package com.example.asus.Core.strategy;



import com.example.asus.Core.client.Piece;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Random;
import java.util.SortedSet;


public class RequestStrategyImplRarest implements RequestStrategy {


	private static final int RAREST_PIECE_JITTER = 42;

	private Random random;

	public RequestStrategyImplRarest() {
		this.random = new Random(System.currentTimeMillis());
	}

	@Override
	public Piece choosePiece(SortedSet<Piece> rarest, BitSet interesting, Piece[] pieces) {
		// Extract the RAREST_PIECE_JITTER rarest pieces from the interesting
		// pieces of this peer.
		ArrayList<Piece> choice = new ArrayList<Piece>(RAREST_PIECE_JITTER);
		synchronized (rarest) {
			for (Piece piece : rarest) {
				if (interesting.get(piece.getIndex())) {
					choice.add(piece);
					if (choice.size() >= RAREST_PIECE_JITTER) {
						break;
					}
				}
			}
		}

		if (choice.size() == 0) return null;

		Piece chosen = choice.get(
				this.random.nextInt(
						Math.min(choice.size(),
								RAREST_PIECE_JITTER)));
		return chosen;
	}
}