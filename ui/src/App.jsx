import React, {Component} from 'react';
import Simulation from "./Simulation";
import './style.css';
import {Container, Dropdown} from "semantic-ui-react";
import 'semantic-ui-css/semantic.min.css'

export default class App extends Component {
    state = {
        results: [],
        resultsLoading: true,
        selectedIdx: -1,
        flip: true
    };

    componentDidMount() {
        fetch('/api/results').then(res => res.json()).then(out => {
            this.setState({
                results: out,
                resultsLoading: false
            })
        });
    }

    render = () => {
        const {results, resultsLoading, selectedIdx, flip} = this.state;
        return <Container className='app-container'>
            <Dropdown
                disabled={resultsLoading}
                fluid
                button
                loading={resultsLoading}
                text={selectedIdx >= 0 ? results[selectedIdx].name + ' - ' + results[selectedIdx].description : "Select Simulation Trace"}
            >
                <Dropdown.Menu>
                <Dropdown.Header icon='tags' content='Simulation Results' />
                <Dropdown.Divider />
                {results.map((res, idx) => {
                    return <Dropdown.Item
                        onClick={() => this.setState({selectedIdx: idx, flip: !flip})}
                        key={res.name + ':' + res.description}
                        active={selectedIdx === idx}
                        description={res.description}
                        text={res.name}
                    />
                })}
                </Dropdown.Menu>
            </Dropdown>
            { selectedIdx >= 0 ? <Simulation key={selectedIdx + ':' + flip} data={results[selectedIdx].trace}/> : null }
        </Container>
    }
}