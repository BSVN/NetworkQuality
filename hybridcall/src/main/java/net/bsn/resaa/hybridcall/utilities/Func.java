package net.bsn.resaa.hybridcall.utilities;

public interface Func<TInput, TOutput> {
	TOutput run(TInput input);
}
