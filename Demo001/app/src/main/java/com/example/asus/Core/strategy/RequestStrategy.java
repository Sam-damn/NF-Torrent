package com.example.asus.Core.strategy;



import com.example.asus.Core.client.Piece;

import java.util.BitSet;
import java.util.SortedSet;



public interface RequestStrategy {


	Piece choosePiece(SortedSet<Piece> rarest, BitSet interesting, Piece[] pieces);
}