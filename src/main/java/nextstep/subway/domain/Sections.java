package nextstep.subway.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.persistence.CascadeType;
import javax.persistence.Embeddable;
import javax.persistence.OneToMany;

@Embeddable
public class Sections {
    private static final int SECTION_MIN_SIZE = 1;

    @OneToMany(mappedBy = "line", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<Section> sections = new ArrayList<>();

    public void add(Section section) {
        if (sections.isEmpty()) {
            sections.add(section);
            return;
        }

        validationNewSection(section);
        updateExistingSection(section);

        sections.add(section);
    }


    public void remove(Station lastStation) {
        if (sections.size() <= SECTION_MIN_SIZE) {
            throw new IllegalArgumentException("상행 종점역과 하행 종점역만 있는 경우(구간이 1개인 경우) 역을 삭제할 수 없습니다.");
        }

        if (!getLastSection().getDownStation().equals(lastStation)) {
            throw new IllegalArgumentException("지하철 노선에 등록된 역(하행 종점역)만 제거할 수 있습니다.");
        }

        sections.remove(getLastSection());
    }

    private List<Station> getStations() {
        List<Station> stations = new ArrayList<>();
        stations.add(sections.get(0).getUpStation());
        stations.addAll(
            sections.stream()
                .map(Section::getDownStation)
                .collect(Collectors.toList())
        );
        return stations;
    }

    public List<Station> getSortedStations() {
        if (sections.isEmpty()) {
            return new ArrayList<>();
        }

        List<Station> stations = new ArrayList<>();
        Station station = getFirstSection().getUpStation();
        Map<Station, Section> stationSectionMap = sections.stream()
            .collect(Collectors.toMap(Section::getUpStation, Function.identity()));

        while (stationSectionMap.containsKey(station)) {
            stations.add(station);
            station = stationSectionMap.get(station).getDownStation();
        }

        stations.add(station);
        return stations;
    }

    private Section getFirstSection() {
        Set<Station> downStations = sections.stream().map(Section::getDownStation).collect(Collectors.toSet());
        return sections.stream()
            .filter(s -> !downStations.contains(s.getUpStation()))
            .findAny()
            .orElseThrow(IllegalArgumentException::new);
    }

    private Section getLastSection() {
        Set<Station> upStations = sections.stream().map(Section::getUpStation).collect(Collectors.toSet());
        return sections.stream()
            .filter(s -> !upStations.contains(s.getDownStation()))
            .findAny()
            .orElseThrow(IllegalArgumentException::new);
    }

    private boolean isAddSectionToFirst(Section section) {
        Section firstSection = getFirstSection();
        return firstSection.getUpStation().equals(section.getDownStation());
    }

    private boolean isAddSectionToLast(Section section) {
        Section lastSection = getLastSection();
        return lastSection.getDownStation().equals(section.getUpStation());
    }

    private void updateExistingSection(Section section) {
        if (isAddSectionToFirst(section) || isAddSectionToLast(section)) {
            return;
        }

        Section targetSection = sections.stream()
            .filter(s -> s.getUpStation().equals(section.getUpStation()))
            .findFirst()
            .orElseThrow(IllegalArgumentException::new);

        if (section.getDistance() >= targetSection.getDistance()) {
            throw new IllegalArgumentException("기존 역 사이 길이보다 크거나 같으면 등록을 할 수 없습니다.");
        }

        targetSection.update(section.getDownStation(), section.getDistance());
    }

    private void validationNewSection(Section section) {
        long existStationCount = getStations().stream()
            .filter(s -> section.getUpStation().equals(s) || section.getDownStation().equals(s))
            .count();

        if (existStationCount == 2) {
            throw new IllegalArgumentException("중복 되는 구간입니다.");
        }

        if (existStationCount == 0) {
            throw new IllegalArgumentException("상행역과 하행역 둘 중 하나는 노선에 포함되어 있어야 합니다.");
        }
    }

}
