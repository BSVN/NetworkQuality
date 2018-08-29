package net.bsn.resaa.hybridcall.utilities;

import java.util.Calendar;
import java.util.Date;

public abstract class TimedCache<T> {
	private T lastValue;
	private int timeoutMilliseconds;
	private Date lastUpdateTime;

	public TimedCache(int timeoutMilliseconds) {
		this.timeoutMilliseconds = timeoutMilliseconds;
	}

	public abstract T getNewValue();

	public T getValue() {
		Date nearestPermittedUpdateTime = new Date();
		nearestPermittedUpdateTime.setTime(System.currentTimeMillis() - timeoutMilliseconds);
		if (lastUpdateTime == null || lastUpdateTime.compareTo(nearestPermittedUpdateTime) < 0) {
			lastValue = getNewValue();
			lastUpdateTime = Calendar.getInstance().getTime();
		}
		return lastValue;
	}
}
