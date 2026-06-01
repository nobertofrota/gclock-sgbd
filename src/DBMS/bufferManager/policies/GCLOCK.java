package DBMS.bufferManager.policies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import DBMS.bufferManager.IPage;

public class GCLOCK extends AbstractBufferPolicy {

	private static final int INITIAL_REFERENCE_COUNT = 1;
	private static final int HIT_REFERENCE_COUNT = 3;

	protected List<IPage> list = Collections.synchronizedList(new ArrayList<IPage>());
	private HashMap<String, Integer> referenceCountByPageId = new HashMap<String, Integer>();
	private int hand = 0;

	public GCLOCK(Integer capacity) {
		super(capacity);
	}

	@Override
	public synchronized IPage find(String pageId) {
		super.numberOfOperation++;

		for (int i = 0; i < list.size(); i++) {
			IPage page = list.get(i);
			if (page != null && page.getPageId().equals(pageId)) {
				hit(page);
				return page;
			}
		}

		super.missCount++;
		return null;
	}

	private void hit(IPage page) {
		action(() -> {
			super.hitCount++;
			page.addHitCount();

			referenceCountByPageId.put(page.getPageId(), HIT_REFERENCE_COUNT);

			if (policyListener != null)
				policyListener.updatePage(page);
			if (policyListener != null)
				policyListener.hit(page);
		});
	}

	@Override
	public void insert(IPage page) {
		action(() -> {
			if (list.size() == super.capacity) {
				replacement();
			}

			alloc(page);
			list.add(page);
			referenceCountByPageId.put(page.getPageId(), INITIAL_REFERENCE_COUNT);

			if (policyListener != null)
				policyListener.insert(page);
		});
	}

	private void replacement() {
		action(() -> {
			if (list.isEmpty()) {
				return;
			}

			if (hand >= list.size()) {
				hand = 0;
			}

			while (true) {
				IPage candidate = list.get(hand);
				int referenceCount = getReferenceCount(candidate);
				System.out.println(
						"[GCLOCK] Hand=" + hand +
						" | Pagina=" + candidate.getPageId() +
						" | Contador=" + referenceCount
				);

				if (referenceCount <= 0) {
					System.out.println("[GCLOCK] Vitima escolhida: " + candidate.getPageId());
					removeAtHand(candidate);
					return;
				}

				referenceCountByPageId.put(candidate.getPageId(), referenceCount - 1);
				advanceHand();
			}
		});
	}

	private void removeAtHand(IPage page) {
		list.remove(hand);
		referenceCountByPageId.remove(page.getPageId());
		free(page);

		if (policyListener != null)
			policyListener.remove(page);
		if (policyListener != null)
			policyListener.setLastRemoved(page);

		if (hand >= list.size() && !list.isEmpty()) {
			hand = 0;
		}
	}

	private void advanceHand() {
		if (list.isEmpty()) {
			hand = 0;
			return;
		}
		hand = (hand + 1) % list.size();
	}

	private int getReferenceCount(IPage page) {
		Integer value = referenceCountByPageId.get(page.getPageId());
		return value == null ? 0 : value.intValue();
	}

	@Override
	public void remove(IPage page) {
		action(() -> {
			int index = list.indexOf(page);
			if (index < 0) {
				return;
			}

			list.remove(index);
			referenceCountByPageId.remove(page.getPageId());
			free(page);

			if (policyListener != null)
				policyListener.remove(page);
			if (policyListener != null)
				policyListener.setLastRemoved(page);

			if (list.isEmpty()) {
				hand = 0;
			} else if (index < hand) {
				hand--;
			} else if (hand >= list.size()) {
				hand = 0;
			}
		});
	}

	@Override
	protected void logicRemoveAll() {
		action(() -> {
			list.clear();
			referenceCountByPageId.clear();
			hand = 0;
		});
	}

	@Override
	public String getName() {
		return "Generalized Clock (GCLOCK)";
	}

	@Override
	public List<IPage> getPages() {
		return list;
	}

	@Override
	public void setPolicyListener(BufferPolicyListener listener) {
		policyListener = listener;
	}

	@Override
	public int getCurrentNumberOfPages() {
		return list.size();
	}

	@Override
	public String toString() {
		return list.toString();
	}
}
